package primer

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

/**
  * Streams Lecture 7 [Operator Fusion And Async Boundaries]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530910
  */

object OperatorFusion extends App {

  implicit val system: ActorSystem = ActorSystem("OperatorFusion")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val simpleSource: Source[Int, NotUsed] = Source(1 to 1000)
  val simpleFlow: Flow[Int, Int, NotUsed] = Flow[Int].map(_ + 1)
  val simpleFlow2: Flow[Int, Int, NotUsed] = Flow[Int].map(_ * 10)
  val simpleSink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  // This runs on SAME ACTOR known as Operator / Component Fusion[As Akka Stream Components are based on Actors]
  // This operator fusion mechanism is something that Akka streams does by default behind the scenes so that it improves throughput
  // End result with such an AKKA stream is that a single CPU core will be used for the complete processing of every single element throughout the entire flow
  // simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run()

  // Equivalent Behaviour
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case x: Int =>
        // flow operations
        val x2 = x + 1
        val y = x2 * 10

        // sink operation
        println(y)
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")
  // (1 to 1000).foreach( simpleActor ! _)

  /*
    But operator fusion causes more harm than good, If the operations are time expensive and let me show you.
   */

  val complexFlow = Flow[Int].map { x =>
    // simulating long computation
    Thread.sleep(1000)
    x + 1
  }

  val complexFlow2 = Flow[Int].map { x =>
    // simulating long computation
    Thread.sleep(1000)
    x * 10
  }

  // There's a 2 second time difference between any of these numbers.
  // That's because both of these threads sleeps are operating on the same actor.
  // simpleSource.via(complexFlow).via(complexFlow2).to(simpleSink).run()

  /**
    * So when operators are expensive it's worth making them run separately in parallel on different actors.
    * So for that we need to introduce the concept of an Async Boundary.
    */

  // Async Boundary
  simpleSource.via(complexFlow).async // runs on one actor
    .via(complexFlow2).async // runs on another separate Actor
    .to(simpleSink) // runs on third Actor
    // .run()

  // So because the time consuming operators from `complexFlow` and `complexFlow2` run on different actors
  // We were able to pipeline these expensive operations and run them in parallel so we're basically able to double our throughput
  // so these async calls are called async boundaries and is the Akka streams API to break operator fusion which Akka streams does by default
  // you can introduce as many async boundaries as you like.

  /**
    * An async boundary contains
    * - everything from the previous boundary (if any) so it includes the previous boundary and
    * - everything between the previous boundary and this boundary
    *
    * Communication in between async boundaries is done via asynchronous actor messages.
    */

  /**
    * Ordering Guarantees
    */
  // All these operators are simply attached to the source without any async boundary
  // which means that all of these operators are fused to the source
  // which means that every element is fully processed in the stream before any new element is admitted into the stream.
  // This is a fully deterministic and guaranteed order
  Source(1 to 3)
    .map( element => {println(s"Flow A: $element"); element})
    .map( element => {println(s"Flow B: $element"); element})
    .map( element => {println(s"Flow C: $element"); element})
    //.runWith(Sink.ignore)

  // Let's see what happens if I put an async boundaries
  // In this case we won't have the same strong ordering guarantees because every single one of these streams will run on a separate actor
  // but we do have one ordering guarantee and that is the relative order of these elements inside every step of the stream.
  Source(1 to 3)
    .map( element => {println(s"Flow A: $element"); element}).async
    .map( element => {println(s"Flow B: $element"); element}).async
    .map( element => {println(s"Flow C: $element"); element}).async
    .runWith(Sink.ignore)

  // Conclusion
  // Async boundaries work best when individual operations are expensive so you want to make them run in parallel
  // BUT we want to avoid async boundaries and stick to operator fusion when operations are comparable with a message paths.


}
