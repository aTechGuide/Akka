package primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Streams Lecture 6 [Materializing Streams]
  *
  * - Running a graph = Materializing Graph
  *
  * Notes in OneNote
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530906
  */

object MaterializingStreams extends App {

  implicit val system = ActorSystem("MaterializingStreams")

  // Materializer is one of these objects that allocates the right resources to running an Akka stream
  implicit val materializer = ActorMaterializer()

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))

  // Calling of the `run` method is an expression and the result of that expression is called a materialized value.
//  val simpleMaterializedValue = simpleGraph.run()

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int]((a,b) => a + b)

  // `sumFuture` is the materialized value obtained by running a graph connecting source and sync
  val sumFuture: Future[Int] = source.runWith(sink)

  import system.dispatcher
  sumFuture.onComplete {
    case Success(value) =>
      println(s"Sum = $value")
    case Failure(exception) =>
      println(s"Sum can NOT be computed $exception")
  }

  /**
    * Choosing Materialized Value
    *
    * - By default leftmost materialized value is kept in the graph.
    * - But we can have further control over which materialized value we can choose by using different methods.
    */
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(x => x + 1)
  val simpleSink = Sink.foreach[Int](println)

  val graph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
  graph.run().onComplete {
    case Success(_) => println("Stream Processing Finished")
    case Failure(exception) => println(s"Stream Processing failed $exception")
  }

  // Sugars
  val sum: Future[Int] = Source(1 to 10).runWith(Sink.reduce[Int](_ + _)) // Equivalent: Source(1 to 10).to(Sink.reduce[Int](_+_))(Keep.right)
  Source(1 to 10).runReduce(_ + _) // Same thing as above

  // backwards
  Sink.foreach[Int](println).runWith(Source.single(42))
  // source(..).to(Sink...).run

  // Running components both ways
  Flow[Int].map(x => 2 * x).runWith(simpleSource, simpleSink)

  /**
    * Exercise
    * 1) Return last element out of a source
    * 2) Compute total word count out of a stream of sentences
    */

  // 1
  val f1 = Source(1 to 10).toMat(Sink.last[Int])(Keep.right).run()
  val f2 = Source(1 to 10).runWith(Sink.last[Int])
  f1.onComplete {
    case Success(lastValue) => println(s"lastValue = $lastValue")
  }

  // 2
  val sentenceSource = Source(List(
    "Akka is awesone",
    "I love streams",
    "materialized values are killing me"
  ))

  val wordCountSink = Sink
    .fold[Int, String](0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)

  val g1 = sentenceSource.toMat(wordCountSink)(Keep.right).run()
  val g2 = sentenceSource.runWith(wordCountSink)
  val g3 = sentenceSource.runFold(0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)

}
