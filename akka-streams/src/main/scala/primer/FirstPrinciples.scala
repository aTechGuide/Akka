package primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}

import scala.concurrent.Future

/**
  * Streams Lecture 5 [First Principles]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530902
  */
object FirstPrinciples extends App {

  implicit val system: ActorSystem = ActorSystem("FirstPrinciples")

  // Materialize allows the running of Akka streams components
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // Source
  val source: Source[Int, NotUsed] = Source(1 to 10)

  // Sink
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  // Graph is the definition of an Akka stream
  val graph:  RunnableGraph[NotUsed] = source.to(sink)

  // graph.run()

  /**
    * Flows
    * - They transform elements
    */

  val flow: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x + 1)

  val sourceWithFlow: Source[Int , NotUsed] = source.via(flow)

  val flowWithSink: Sink[Int, NotUsed] = flow.to(sink)

//  sourceWithFlow.to(sink).run()
//  source.to(flowWithSink).run()
//  source.via(flow).to(sink).run()

  // nulls are NOT allowed [Use Options Instead]
  // val illegalSource = Source.single[String](null)
  // illegalSource.to(Sink.foreach(println)).run()

  /**
    * Kinds of Sources
    */
  // Finite
  val finiteSource: Source[Int, NotUsed] = Source.single(1)
  val anotherFiniteSource: Source[Int, NotUsed] = Source(List(1, 2, 3))
  val emptySource: Source[Int, NotUsed] = Source.empty[Int]

  // Infinite Source
  val infiniteSource: Source[Int, NotUsed] = Source(Stream.from(1)) // Do NOT Confuse Akka Stream with a "collection" Stream

  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource: Source[Int, NotUsed] = Source.fromFuture(Future(42))

  /**
    * Sinks
    */

  val boringSink: Sink[Any, Future[Done]] = Sink.ignore
  val forEachSink: Sink[String, Future[Done]] = Sink.foreach[String](println)
  val headSink: Sink[Int, Future[Int]] = Sink.head[Int] // Retrieves head and then closes the Stream
  val foldSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((a,b) => a + b)

  /**
    * Flows
    */
  val mapFlow: Flow[Int, Int, NotUsed] = Flow[Int].map(x => 2 * x)
  val takeFlow: Flow[Int, Int, NotUsed] = Flow[Int].take(5)
  // have drop, filter
  // NOT have flatMap

  // source -> flow -> ..... -> sink

  val doubleFlowGraph: RunnableGraph[NotUsed] = source.via(mapFlow).via(takeFlow).to(sink)
  // doubleFlowGraph.run()

  // Syntactic Sugars
  val mapSource = Source(1 to 10).map(x => x) // Equivalent to `Source(1 to 10).via(Flow[Int].map(x => x * 2))`

  // Run Stream Directly
  // mapSource.runForeach(println) // mapSource.to(Sink.foreach[Int](println)).run()

  // OPERATORS = COMPONENTS

  /**
    * Exercise
    * - Stream take name of Person
    * - Keep first 2 names with length > 5 characters
    */

  val nameSource = Source(List("Kamran", "ali", "palash", "daud"))

  nameSource.filter(_.length > 5).take(2).runForeach(println)

}
