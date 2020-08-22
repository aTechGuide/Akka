package graphs

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, FlowShape, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Streams Lecture 12 [Graph Materialized Values]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530946#overview
  */

object GraphMaterializedValues extends App {

  implicit val system: ActorSystem = ActorSystem("OpenGraphs")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val wordSource = Source(List("Akka", "is", "awesome", "rock", "the", "jvm"))
  val printerSink: Sink[String, Future[Done]] =  Sink.foreach[String](println)
  val counterSink: Sink[String, Future[Int]] = Sink.fold[Int, String](0)((count, _) => count + 1) // Sink that counts how many strings get inside it and the `Sink.fold` is a sink that actually exposes materialized value.

  /*
      GOAL
      - I would like to create a composite component that acts like a Sink which prints out all strings which are lowercase
      - and also counts the strings that are short like less than five characters.

      So we need this composite component that inside will have a small broadcast and one of the branches from that broadcast will filter out all the strings which are lowercase and then we'll feed them to the printer.
      And the other branch of the broadcast will filter only the strings that are short and will feed that into the counter sink.
   */
  // Step 1
  val complexWordSink = Sink.fromGraph(
    GraphDSL.create(printerSink, counterSink)((printerMatValue, counterMatValue) => counterMatValue) { implicit builder => (printerShape, counterShape) =>
      import GraphDSL.Implicits._

      // Step 2 Shapes
      val broadcast = builder.add(Broadcast[String](2))
      val lowercaseFilter: FlowShape[String, String] = builder.add(Flow[String].filter(word => word == word.toLowerCase))
      val shortStringFilter: FlowShape[String, String] = builder.add(Flow[String].filter(_.length < 5))

      // Step 3 Connections
      broadcast ~> lowercaseFilter ~> printerShape
      broadcast ~> shortStringFilter ~> counterShape

      // Step 4 The Shape
      SinkShape(broadcast.in)
    }
  )

  val shortStringsCountFuture = wordSource.toMat(complexWordSink)(Keep.right)
    .run()

  import system.dispatcher
  shortStringsCountFuture.onComplete {
    case Success(count) => println(s"The total number of short strings is $count")
    case Failure(exception) => println(s"The count of short Strings Failed $exception")
  }

  /**
    * Exercise
    */

  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {

    val counterSink: Sink[B, Future[Int]] = Sink.fold[Int, B](0)((count, element) => count + 1)

    Flow.fromGraph(
      GraphDSL.create(counterSink) { implicit builder => counterSinkShape =>
        import GraphDSL.Implicits._

        // Step 2
        val broadcast = builder.add(Broadcast[B](2))
        val originalFlowShape = builder.add(flow)

        // Step 3
        originalFlowShape ~> broadcast ~> counterSinkShape

        FlowShape(originalFlowShape.in, broadcast.out(1))
      }
    )
  }

  val simpleSource = Source( 1 to 42)
  val simpleFlow: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x)
  val simpleSink = Sink.ignore

  val enhancedFlowCountFuture: Future[Int] = simpleSource
    .viaMat(enhanceFlow(simpleFlow))(Keep.right)
    .toMat(simpleSink)(Keep.left)
    .run()

  enhancedFlowCountFuture.onComplete {
    case Success(count) => println(s"$count elements went through this flow")
    case Failure(exception) => println(s"Failure: $exception")
  }
}
