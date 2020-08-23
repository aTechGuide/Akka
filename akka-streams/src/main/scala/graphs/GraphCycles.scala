package graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.MergePreferred.MergePreferredShape
import akka.stream.{ActorMaterializer, ClosedShape, FlowShape, Graph, OverflowStrategy, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip, ZipWith}

/**
  * Streams Lecture 14 [Graph Cycles]
  *
  * GOAL
  * - How to incorporate cycles and feedback loops into the graphs that we built with the graph DSL which opens the door for a range of possibilities
  * - Graph cycles must be used responsibly because otherwise you'll get a lot of headaches
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530952
  */

object GraphCycles extends App {

  implicit val system: ActorSystem = ActorSystem("GraphCycles")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val accelerator: Graph[ClosedShape, NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
                   mergeShape <~ incrementerShape

    ClosedShape
  }

  // RunnableGraph.fromGraph(accelerator).run() // Prints -> Accelerating 1

  // ^ So the problem here is that we always increase the number of elements in the graph and we never get rid of any.
  // So `sourceShape` always increases the number of elements and the elements are always cycled inside the graph.
  // Now our components will end up buffering elements and quickly become full so that everyone starts to be back pressured
  //   - starting from the `sourceShape` to the `incrementalShape` to the `mergeShape` back.
  //   - And then finally back pressure the entire source which stops emitting elements altogether.
  // So this is known as a Cycle Deadlock.

  // Solutions
  // # 1
  // This problem happens in particular if you have merges in your cycles but there is a special version of a merge which might break this deadlock.
  // And that is MergePreferred which is a special version of a merge which has a preferential input
  // and whenever a new element is available on that input it will take that element and pass it on regardless of what elements are available on the other outputs.

  val actualAccelerator: Graph[ClosedShape, NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    // ^ Argument for this MergePreferred is 1 instead of 2 because this merge preferred has its preferred port plus one additional input port

    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }

  // RunnableGraph.fromGraph(actualAccelerator).run() // Prints -> Accelerating 1

  // ^ Now the reason why this graph works is that
  // - The merge waits for an element on its preferred port but at the beginning there is no element on its preferred port so it's fine to take an element out of the source.

  // Now after the first element is fed from the source into the merge and into the increment or and fed back into the preferred port
  // then the merge will always take the preferred port and feed it into the increment or which always accelerates that number.

  // Solution #2 which involves buffers
  // I mentioned at the beginning that our components started dead locking because they started buffering elements
  // once their buffers are full they start back pressuring each other until nobody is there to process anymore elements through the graph.

  // So we need to override that buffer overflow strategy to actually drop some elements circulating through the graph and actually start to break that deadlock.

  val bufferRepeater: Graph[ClosedShape, NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"Repeater $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape

    ClosedShape
  }

  // RunnableGraph.fromGraph(bufferRepeater).run()

  // ^ So if we take a careful look some of the numbers are repeated in the graph some quite a lot of times like 3 or 4 times.
  // But on average the numbers are increasing which means that new elements are fed from the source.
  // And this is how we break the deadlock with buffers.

  // Important takeaway that I want you to remember from these couple of examples is that
  // if we add cycles in our graphs we risk deadlocking especially if we have unboundedness in our cycles.
  // So in our first example we always increased the number of elements circulating through the graph
  // while those elements end up accumulating in the components buffers which end up back pressuring each other.
  // So one solution to that is to add bounce to the number of elements
  // so this is what we did in both examples.
  // The second example is more illustrative of the bounds because we explicitly added a buffer with the overflow strategy.
  // But that's what we did in the first example as well with the merge preferred because we limited the number of elements circular into the graph to 1.
  // So this is the challenge that we will have to face if you add cycles in your graphs.
  // So the tradeoff is between boundedness versus liveliness. That is the capacity of the graph to not deadlock.

  // Exercise
  // Create a fan-in shape which takes two inputs which will be fed with exactly one number
  // The output will emit an infinite FIBONACCI sequence based off of those two numbers
  // For the test I'm going to send the numbers 1 and 1 because those are the two Fibonacci numbers
  // and the output will emit an infinite Fibonacci sequence based off of those two numbers.
  // So that means the numbers will be 1 2 3 5 8 and so on and so forth forever.
  // So needless to say that this component will probably internally have a cycle or multiple cycles depending on how you decide to implement it.

  val fibonacciGenerator: Graph[UniformFanInShape[BigInt, BigInt], NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val zip = builder.add(Zip[BigInt, BigInt])
    val mergeShape: MergePreferredShape[(BigInt, BigInt)] = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val fiboLogic: FlowShape[(BigInt, BigInt), (BigInt, BigInt)] = builder.add(Flow[(BigInt, BigInt)].map { pair =>
      val last = pair._1
      val prev = pair._2

      Thread.sleep(100)
      (last + prev, last)
    })

    val broadcast = builder.add(Broadcast[(BigInt, BigInt)](2))
    val extractLast = builder.add(Flow[(BigInt, BigInt)].map(_._1))


    zip.out ~> mergeShape ~> fiboLogic ~> broadcast ~> extractLast
               mergeShape.preferred  <~   broadcast

    UniformFanInShape(extractLast.out, zip.in0, zip.in1)
  }

  val fiboGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val source1 = builder.add(Source.single[BigInt](1))
      val source2 = builder.add(Source.single[BigInt](1))
      val sink = builder.add(Sink.foreach[BigInt](println))
      val fibo = builder.add(fibonacciGenerator)

      source1 ~> fibo.in(0)
      source2 ~> fibo.in(1)
      fibo ~> sink

      ClosedShape
    }
  )

  fiboGraph.run()








}
