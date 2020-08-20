package graphs

import java.util.Date

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FanInShape2, FanOutShape2, FlowShape, SinkShape, SourceShape, UniformFanInShape, UniformFanOutShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}

/**
  * Streams Lecture 10, 11 [Open Graphs and Graph Shapes]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530934
  */

object OpenGraphs extends App {

  implicit val system: ActorSystem = ActorSystem("OpenGraphs")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /*
    Let's create a composite source that concatenates two sources.
    - It emits all the elements from the first source
    - then all the elements from the second
   */

  val firstSource = Source( 1 to 10)
  val secondSource = Source( 42 to 1000)

  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val concat = builder.add(Concat[Int](2))

      firstSource ~> concat
      secondSource ~> concat

      SourceShape(concat.out)
    }
  )

  sourceGraph
    .to(Sink.foreach[Int](println))
    // .run()

  /*
    Complex Sink
   */
  val sink1 = Sink.foreach[Int](x => println(s"Meaningful thing 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Meaningful thing 2: $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() {implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))

      broadcast ~> sink1
      broadcast ~> sink2

      SinkShape(broadcast.in)
    }
  )

  firstSource.to(sinkGraph)
    //.run()

  /**
    * Complex Flow
    * Write your own flow that's composed of two other flows
    */

  val incrementer = Flow[Int].map(_ + 1)
  val multiplier = Flow[Int].map(_ * 10)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // Everything operates on SHAPES
      val incrementerShape = builder.add(incrementer)
      val multiplierShape = builder.add(multiplier)

      incrementerShape ~> multiplierShape

      FlowShape(incrementerShape.in, multiplierShape.out) // SHAPE
    } // STATIC Graph
  ) // Component

  firstSource.via(flowGraph).to(Sink.foreach[Int](println))
    //.run()

  /**
    * Exercise: Is it possible to create a Flow from a sink and a source?
    *
    * So the answer is that the Akka streams API does not forbid such a flow.
    */

  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] =
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>

        val sourceShape = builder.add(source)
        val sinkShape = builder.add(sink)

        FlowShape(sinkShape.in, sourceShape.out)
      }
    )

  // We have it in Library itself
  val f = Flow.fromSinkAndSource(Sink.foreach[String](println), Source( 1 to 10))

  // Such a flow would technically be fine but the problem here is that there is no connection between the components involved.
  // So the danger is that if the elements going into the sink for example are finished then the source has no way of stopping the stream.
  // If this flow ends up connecting various parts of your graph that's why Akka streams has a special version of this method called `fromSinkAndSourceCoupled`

  val f2 = Flow.fromSinkAndSourceCoupled(Sink.foreach[String](println), Source( 1 to 10))

  // f2 can send termination signals and back pressure signals between these two otherwise unconnected components

  /**
    * Chapter 11 Starts here
    *
    * - Writing FanIn and FanOut Shapes
    * - Uniform / Non Uniform Shapes
    */

  /*
    Example: Max3 Operator
    - 3 Inputs of Type int
    - Push out maximum of the 3
   */

  // Step 1
  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    // Step 2 Define Auxiliary shapes
    val max1: FanInShape2[Int, Int, Int] = builder.add(ZipWith[Int, Int, Int]((a, b) => math.max(a,b)))
    val max2: FanInShape2[Int, Int, Int] = builder.add(ZipWith[Int, Int, Int]((a, b) => math.max(a,b)))

    // step 3
    max1.out ~> max2.in0

    // step 4
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
    // This shape is called uniform because its inputs receive elements of same Type

  } // Static Graph

  val source1 = Source( 1 to 10)
  val source2 = Source( 1 to 10).map(_ => 5)
  val source3 = Source( (1 to 10).reverse)

  val maxSink = Sink.foreach[Int](x => println(s"Max is : $x"))

  // Step 1
  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // Step 2 Declare Components
      val max3Shape = builder.add(max3StaticGraph)

      // Step 3 Tie them
      source1 ~> max3Shape.in(0)
      source2 ~> max3Shape.in(1)
      source3 ~> max3Shape.in(2)

      max3Shape.out ~> maxSink


      // Step 4
      ClosedShape
    }
  )

  max3RunnableGraph
    //.run()

  // Same for FanOutShapes

  /*
    Non Uniform fan out shape

    Processing bank transactions
    Transactions is suspicious if amount > 10000

    Streams component for transactions
    - output1 -> let transaction go through
    - output2 -> suspicious transaction ids
   */

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)

  val transactionSource = Source( List(
    Transaction("43243243", "Paul", "Jim", 1000, new Date),
    Transaction("43243432243", "Daniel", "Jim", 100000, new Date),
    Transaction("43432243", "Jim", "Alice", 7000, new Date)
  ))

  val bankProcessorSink = Sink.foreach[Transaction](println)
  val suspiciousAnalysisServiceSink = Sink.foreach[String](txnID => println(s"Suspicious Transaction ID: $txnID"))

  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    // step 2 Define Shapes
    val broadcast: UniformFanOutShape[Transaction, Transaction] = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnFilter = builder.add(Flow[Transaction].filter(_.amount > 10000))
    val txnIdExtractor = builder.add(Flow[Transaction].map[String](_.id))

    // Step 3: Tie Shapes
    broadcast.out(0) ~> suspiciousTxnFilter ~> txnIdExtractor

    // Step 4
    new FanOutShape2(broadcast.in, broadcast.out(1), txnIdExtractor.out)
  }

  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // Step 2
      val suspiciousTxnShape: FanOutShape2[Transaction, Transaction, String] = builder.add(suspiciousTxnStaticGraph)

      // Step 3
      transactionSource ~> suspiciousTxnShape.in
      suspiciousTxnShape.out0 ~> bankProcessorSink
      suspiciousTxnShape.out1 ~> suspiciousAnalysisServiceSink

      // Step 4
      ClosedShape

    }
  )

  suspiciousTxnRunnableGraph
    .run()

}
