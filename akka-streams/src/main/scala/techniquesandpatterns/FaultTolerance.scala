package techniquesandpatterns

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.{ActorAttributes, ActorMaterializer}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.util.Random

/**
  * Streams Lecture 18 [Fault Tolerance and Error Handling]
  *
  * How to React to failures in Streams
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530972s
  */

object FaultTolerance extends App {

  implicit val system: ActorSystem = ActorSystem("FaultTolerance")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // 1 logging

  // For actual data that goes out of the source that's logged at log level debug whereas failures are logged at log level error.
  val faultySource: Source[Int, NotUsed] = Source( 1 to 10).map(e => if (e == 6) throw new RuntimeException else e)
  faultySource.log("trackingElements").to(Sink.ignore)
    //.run

  // NOTE -> When an operator in a Akka stream throws an exception it usually terminates the whole stream.
  // That is all the upstream operators are canceled an all the downstream operators will be informed by a special message.

  // So here's how we can recover from such an exception

  // 2  - Gracefully terminating a stream
  faultySource.recover {
    case _: RuntimeException => Int.MinValue // after this value is returned the stream is stopped but it doesn't crash like we did in the first example.
  }.log("gracefulSource")
    .to(Sink.ignore)
    // .run

  // 3 Recover with Another Stream
  // Another version of `recover` which doesn't complete the stream with a value but instead it replaces the entire upstream with another stream
  faultySource.recoverWithRetries(3, {
    case _: RuntimeException => Source(90 to 99) // when we plug this (i.e. `recoverWithRetries`) composite source to a consumer if the faulty source fails it will be replaced with `Source(90 to 99)` and it will be attempted three times
  }).log("recoverWithRetries")
    .to(Sink.ignore)
    //.run

  // 4 - Backoff Supervision
  // When an actor failed its supervisor automatically tried to restart it after an exponentially increasing delay.
  // This Backoff supervisor also add some randomness so that if failed actors were trying to access a resource at the same time not all of them should attempt at that exact moment.
  // So in the same fashion stream operators that access external resources for example databases or TCP connections might fail and this backup supervisor might prove useful.

  import scala.concurrent.duration._
  val restartSource = RestartSource.onFailuresWithBackoff(
    minBackoff = 1 second,
    maxBackoff = 30 seconds,
    randomFactor = 0.2 // this prevents multiple components from trying to restart at the same time if some resource failed because if they do they might bring the resource down again
  )(() => {
    val randomNumber = new Random().nextInt(20)
    Source(1 to 10).map(elem => if (elem == randomNumber) throw new RuntimeException else elem)

  }) //<- This function will be called on every attempt, which returns another source

  restartSource
    .log("restartBackoff")
    .to(Sink.ignore)
    //.run


  // So the way that this works is that the function (that we have passed in second argument list) is called first.
  // And when we plug this `restartSource` to whatever consumer it has
  // - if the value here fails for some reason it will be swapped and the back off supervision will kick in after 1 second
  //   so after 1 second this function will be called again and this new result will be plugged into you that consumer
  // - if that one fails as well then the back of interval will double. So the next time this function will be called will be after 2 seconds
  //   and if it fails after 4 seconds and if it fails again after 8 seconds up to a maximum of 30 seconds.

  // 5 Supervision Strategy
  // So unlike actors Akka streams operators are not automatically subject to a supervision strategy but they must explicitly be documented to support them. And by default they just fail.

  val numbers: Source[Int, NotUsed] = Source(1 to 20).map(e => if( e == 13) throw new RuntimeException else e)
    .log("supervision")

  val supervisedNumbers = numbers.withAttributes(ActorAttributes.supervisionStrategy {
    // this supervision strategy takes a partial function between a throwable and a supervision directive which can be resume, stop and restart.
    // Resume -> Skips faulty element and let the stream go through
    // Stop -> Simply stops the stream
    // Restart -> restart will do the same thing as resume but it will also clear the internal state of the component so for example in the case of folds or reduces or scans or any kind of component that accumulates internal state.

    case _:RuntimeException => Resume
    case _ => Stop
  })

  supervisedNumbers
    .to(Sink.ignore)
    .run




}
