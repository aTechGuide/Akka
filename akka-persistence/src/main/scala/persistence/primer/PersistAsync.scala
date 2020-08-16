package persistence.primer

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor
import com.typesafe.config.ConfigFactory

/**
  * Persistence Lecture 12 [Persist Async]
  *
  * - Persist Async is a special version of persist that relaxes some delivery and ordering guarantees.
  * - And it's particularly designed for high throughput use cases where you need to process commands at a high rate in your persistent actors
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002448
  */

object PersistAsync extends App {

  case class Command(contents: String)
  case class Event(contents: String)

  object CriticalStreamProcessor {
    def props(eventAggregator: ActorRef) = Props(new CriticalStreamProcessor(eventAggregator))
  }

  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {

    override def persistenceId: String = "critical-stream-processor"

    override def receiveCommand: Receive = {
      case Command(contents) =>
        eventAggregator ! s"Processing $contents"

        // The difference between persist and persist a sink is "the time gap"
        // Persisting is an asynchronous operation and sends this event to the Journal and when the Journal has persisted the event the handler will fire up.
        // The `/* the time gap */`
        // - In the case of persist we stash every single incoming message.
        // - In the case of `persistAsync` we do not stash messages in this time gap.

        // The thing is we're sending two commands to our Stream processor actor because the time gaps are very large.
        // It's very likely that the second command ends up during one of these time gaps.
        // When it does it will not be stashed and it will be processed. Which explains the "Processing command1" and "Processing command2" right after

        // However the common ground between persist and `persistAsync` is that they're both based on sending messages.

        // So `persistAsync` for the first Command and `persistAsync` for the second Command will happen in order.
        // So Command1 will get the journal before Command2 and as a consequence the first handler will always be executed before the second handler.
        persistAsync(Event(contents)) /* the time gap */ { e =>
          eventAggregator ! e
        }

        // some actual computation
        val processedContents = contents + "_processed"

        persistAsync(Event(processedContents)) { e =>
          eventAggregator ! e
        }

    }

    override def receiveRecover: Receive = {
      case message =>
        log.info(s"Recovered: $message")
    }
  }

  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = {
      case message =>
        log.info(s"$message")
    }
  }


  val system = ActorSystem("PersistAsync", ConfigFactory.load("application-persistence.conf"))

  val eventAggregator = system.actorOf(Props[EventAggregator], "eventAggregator")
  val streamProcessor = system.actorOf(CriticalStreamProcessor.props(eventAggregator), "streamProcessor")

  streamProcessor ! Command("command1")
  streamProcessor ! Command("command2")

  /*

    - `persistAsync` has upper hand in performance than `persist` [As messages are NOT getting stashed in `persistAsync` during /* the time gap */]
    - When we need ordering `persistAsync` is bad e.g. when our state depends on it
   */

}
