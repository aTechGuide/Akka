package advaned

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source, SubFlow}

import scala.util.{Failure, Success}

/**
  * Streams Lecture 21 [Sub Streams]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530988
  */

object Substreams extends App {

  implicit val system: ActorSystem = ActorSystem("DynamicStreamHandling")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /*
    1 - Grouping a Stream by a certain function
   */

  val wordSource: Source[String, NotUsed] = Source(List("Akka", "is", "amazing", "learning", "substrings"))
  val groups: SubFlow[String, NotUsed, wordSource.Repr, wordSource.Closed] =
    wordSource.groupBy(30, word => if(word.isEmpty) '\0' else word.toLowerCase.charAt(0))
  // if the element creates a new key it will create a new substrings
  // if we attach a consumer then every single sub stream will have a different instance of that consumer

  groups
    .to(Sink.fold(0)((count, word) => {
    val newCount = count + 1
    println(s"I received $word, count is $newCount")
    newCount
  }))
    .run

  /*
    2 Merge Sub Streams back
   */
  val textSource = Source(List(
    "I love Akka Streams",
    "this is amazing",
    "learning from Rock the JVM"
  ))

  val totalCharCountFuture = textSource
    .groupBy(2, string => string.length % 2)
    .map(_.length) // do your expensive computation here
    // `2` is the cap on the number of sub streams that can merge at any one time.
    // If cap is lower than # of sub streams then those sub streams that are starting to get merged must complete before other streams can be merged in as well.
    // So this risks dead locking if your sub streams are infinite.
    // For unbounded use `mergeSubstreams` method
    .mergeSubstreamsWithParallelism(2)
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run

  import system.dispatcher
  totalCharCountFuture.onComplete {
    case Success(value) => println(s"Total car count $value")
    case Failure(exception) => println(s"Car computation failed with $exception")
  }

  /*
    3 Splitting a stream into sub streams, when condition is met
   */
  val text = "I love Akka Streams\n" + "this is amazing\n" + "learning from Rock the JVM\n"

  val anotherCarCountFuture = Source(text.toList)
    .splitWhen(c => c == '\n') // when the incoming character is equal to \n a new sub stream will be formed on the spot and all further characters will be sent to that sub stream until the character incoming is equal to \n again.
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run

  anotherCarCountFuture.onComplete {
    case Success(value) => println(s"Total car count alternative $value")
    case Failure(exception) => println(s"Car computation failed with $exception")
  }

  /*
    4 Flattening
   */
  val simpleSource = Source(1 to 3)
  simpleSource.flatMapConcat(x => Source(x to (3 * x))).runWith(Sink.foreach(println))

  // `2` is the cap on the number of sub streams that can be merged at any one time
  simpleSource.flatMapMerge(2, x => Source(x to (3 * x))).runWith(Sink.foreach(println))

}
