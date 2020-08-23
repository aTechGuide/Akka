package techniquesandpatterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout

import scala.concurrent.duration._

/**
  * Streams Lecture 15 [Integration with Actors]
  *
  * So it's only natural that we make Akka Streams interact with actors
  * - Actors can be inserted at any point inside an Akka stream
  * - Actors can act as a source of elements
  * - Actors can act as a destination of elements
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530954
  */

object ActorIntegration extends App {

  implicit val system: ActorSystem = ActorSystem("ActorIntegration")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Just received a string: $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Just received a number : $n")
        sender() ! (2 * n)

      case _ =>
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  val numberSource = Source( 1 to 10)

  /*
    Actor as a Flow
   */
  implicit val timeout = Timeout(2 seconds)
  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)
  // ^ This flow under the hood is based on the ask pattern on `simpleActor` actor
  // Now the `parallelism` factor says how many messages can be in this actor's mailbox at any one time before the actor starts back pressuring.
  // So if you send too many messages to this actor and the mailbox is greater than four messages then this Actor will start back pressuring its source.
  // So you know actors are effectively single threaded so they can't process more than one message at a time
  // but parallelism greater than one still has a performance advantage because
  // if the actor has a message in its mailbox after it finishes processing its current message it will fetch the element from its mailbox immediately.

  // Now also from the Ask pattern those futures can be completed in theory with anything at all
  // which is why we often type them down to whatever type is relevant for us
  // In our case we need to type our flow to be from Int to Int.
  // So the `actorBasedFlow` will be a flow from Int which is the receiving type to Int which is the destination type.

  //numberSource.via(actorBasedFlow).to(Sink.ignore).run()

  // equivalent way
  //numberSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.ignore)

  /*
    Actor As a Source
   */
  val actorPoweredSource: Source[Int, ActorRef] = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
  val materializedActorRef: ActorRef = actorPoweredSource.to(Sink.foreach[Int](number => println(s"Actor powered flow got number: $number"))).run()

  materializedActorRef ! 10

  // terminating the stream
  materializedActorRef ! akka.actor.Status.Success("complete")

  /*
    Actor As a Sink Or Destination
    - It will need to support an initialization message which will be sent first by whichever component ends up connected to this actor powered sink.
    - We need to support an acknowledged message which is sent from this actor to the stream to confirm the reception of an element
      because the absence of this acknowledged message will be interpreted as back pressure.
    - We also need to support a complete message
    - We also need to support a function to generate a message in case the stream throws an exception.

   */
  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream Initialized")
        sender() ! StreamAck // the absence of this acknowledged message will be interpreted as back pressure

      case StreamComplete =>
        log.info("Stream complete")
        context.stop(self)

      case StreamFail(ex) =>
        log.warning(s"Stream failed : $ex")

      case message =>
        log.info(s"Message $message has come to its final resting point.")
        sender() ! StreamAck // the absence of this acknowledged message will be interpreted as back pressure
    }
  }

  val destinationActor = system.actorOf(Props[DestinationActor], "destinationActor")

  val actorPoweredSink = Sink.actorRefWithAck[Int](
    destinationActor,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamComplete,
    ackMessage = StreamAck,
    onFailureMessage = throwable => StreamFail(throwable) // optional
  )

  Source(1 to 10).to(actorPoweredSink).run()

  // Another kind of Actor Powered Sink [Not Recommended as its NOT capable of providing back pressure]
  // Sink.actorRef()

}
