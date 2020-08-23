package graphs

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FanInShape2, UniformFanInShape, UniformFanOutShape}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Streams Lecture 9 [Intro to Graphs and Graph DSL]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530926
  */

object GraphBasics extends App {

  implicit val system: ActorSystem = ActorSystem("GraphBasics")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val input: Source[Int, NotUsed] = Source(1 to 10)
  val incrementer: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x + 1) // Hard Computations
  val multiplier: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x * 10) // Hard Computations
  val output: Sink[(Int, Int), Future[Done]] = Sink.foreach[(Int, Int)](println)

  // Step 1: Setting up the fundamentals for the graph
  val graph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => // builder = MUTABLE Data Structure
      import GraphDSL.Implicits._

      // Step 2: Add necessary components of this graph
      val broadcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2)) // Fan-out operator
      val zip: FanInShape2[Int, Int, (Int, Int)] = builder.add(Zip[Int, Int]) // fan-in operator

      // Step 3: Tying up the components
      input ~> broadcast // Input feeds into broadcast

      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      zip.out ~> output

      // Step 4: Return a closed Shape
      ClosedShape // FREEZE the builder Shape
      // After we returned the closed shape the builder becomes immutable and this shape will be used in constructing the graph

      // Returns a shape object
    } // static graph
  ) // Runnable Graph

//  val matValue: NotUsed = graph.run() // run the graph and materialize it
//  println(s"Value returned is $matValue") // Prints -> Value returned is NotUsed

  /**
    * Exercise 1: Feed a source into 2 sinks at the same time
    */

  val firstSink = Sink.foreach[Int](x => println(s"First Sink: $x"))
  val secondSink = Sink.foreach[Int](x => println(s"Second Sink: $x"))

  val graph2: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))

      input ~> broadcast ~> firstSink // implicit port numbering
               broadcast ~> secondSink

//      broadcast.out(0) ~> firstSink
//      broadcast.out(1) ~> secondSink

      ClosedShape
    }
  )

  // graph2.run()

  /**
    * Exercise 2: Balance
    */

  val fastSource = input.throttle(5, 1 second)
  val slowSource = input.throttle(2, 1 second)

  val sink1 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 1 number of elements: $count")
    count + 1
  })

  val sink2 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 2 number of elements: $count")
    count + 1
  })

  val graph3: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val merge: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))
      val balance: UniformFanOutShape[Int, Int] = builder.add(Balance[Int](2))

      fastSource ~> merge
      slowSource ~> merge

      merge ~> balance ~> sink1
               balance ~> sink2

      ClosedShape
    }
  )

  graph3.run()

}
