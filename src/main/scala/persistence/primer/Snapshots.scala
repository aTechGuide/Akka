package persistence.primer

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

/**
  * Persistence Lecture 10 [Snapshots]
  *
  * One of the problems with Akka persistence is that long lived persistent actors may take a long time to recover if they save lots and lots of events.
  * And the solution to that is to save checkpoints along the way known as snapshots.
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002444#overview
  */

object Snapshots extends App {

  // COMMANDS
  case class ReceivedMessage(contents: String)
  case class SentMessage(contents: String)

  // EVENTS
  case class ReceivedMessageRecord(id: Int, contents: String)
  case class SendMessageRecord(id: Int, contents: String)


  object Chat {
    def props(owner: String, contact: String) = Props(new Chat(owner, contact))
  }

  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {

    val MAX_MESSAGES = 10
    var currentMessageId = 0
    val lastMessages = new mutable.Queue[(String, String)]()

    var commandsWithoutCheckpoint = 0

    override def persistenceId: String = s"$owner-$contact-chat"

    override def receiveCommand: Receive = {
      case ReceivedMessage(contents) =>
        persist(ReceivedMessageRecord(currentMessageId, contents)) { _ =>
          log.info(s"Received Message $contents")

          maybeReplaceMessage(contact, contents)
          currentMessageId += 1

          mayBeCheckpoint()

        }
      case SentMessage(contents) =>

        persist(SendMessageRecord(currentMessageId, contents)) { _ =>
          log.info(s"Sent Message $contents")

          maybeReplaceMessage(owner, contents)
          currentMessageId += 1

          mayBeCheckpoint()

        }

      case "print" =>
        log.info(s"Most recent messages: $lastMessages")

      // Snapshot Related Messages
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"Saving Snapshot succedded: $metadata")

      case SaveSnapshotFailure(metadata, throwable) =>
        log.warning(s"Saving snapshot $metadata failed because of $throwable")
    }

    override def receiveRecover: Receive = {
      case ReceivedMessageRecord(id, contents) =>
        log.info(s"Recovered $contents with id = $id")

        maybeReplaceMessage(contact, contents)
        currentMessageId = id

      case SendMessageRecord(id, contents) =>
        log.info(s"Recovered sent message $id: $contents")
        maybeReplaceMessage(owner, contents)
        currentMessageId = id

      case SnapshotOffer(metadata, contents) =>
        log.info(s"Recovered Snapshot: $metadata")
        contents.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    def mayBeCheckpoint(): Unit = {
      commandsWithoutCheckpoint += 1

      if (commandsWithoutCheckpoint >= MAX_MESSAGES) {
        log.info("Saving checkpoint ..")
        saveSnapshot(lastMessages) // asynchronous operation

        commandsWithoutCheckpoint = 0
      }
    }

    def maybeReplaceMessage(sender: String, contents: String): Unit = {

      if (lastMessages.size >= MAX_MESSAGES) {
        lastMessages.dequeue()
      }

      lastMessages.enqueue((sender, contents))
    }

  }

  val system = ActorSystem("Snapshots", ConfigFactory.load("application-persistance.conf"))
  val chat = system.actorOf(Chat.props("daniel123", "martin"))


//  for (i <- 1 to 100000) {
//    chat ! ReceivedMessage(s"Akka Rocks $i")
//    chat ! SentMessage(s"Akka Rules $i")
//
//  }

  chat ! "print"

  /*
    How Recovers is So fast in Snapshot
    - When we are using snapshots we're basically telling Akka not to recover our entire event history but rather the entire event history since the latest snapshot.

    For example,
    - If a persistent actor has a bunch of events e.g.
      EVENT 1
      EVENT 2
      EVENT 3 and then saves a couple of snapshots like
      SNAPSHOT 1 one and then persist the more events like
      EVENT 3
      EVENT 4
      SNAPSHOT 2
      EVENT 5
      EVENT 6

      Now during a recovery not all of the events persisted will be replayed like EVENT 1 2 3 and 4.
      The only things that are going to get replayed during recovery gonna be SNAPSHOT 2 i.e the latest snapshot and all events since that snapshot like EVENT 5 and EVENT 6.

      That is why our actor here is so fast because every 10 messages we're saving a snapshot.

      So at most we're recovering the latest snapshot and 10 more events which is super quick.
   */

  /*
    Pattern
    - So after each persist maybe save a snapshot. [logic is up to us]
    - If we've saved a snapshot we should handle the `SnapshotOffer` message in `receiveRecover`.
    - [optional, best practice] We should see the `SaveSnapshotSuccess` and `SaveSnapshotFailure` and handle those in receive command.

   */

}
