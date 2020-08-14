package persistence.stores

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.typesafe.config.ConfigFactory

/**
  * Persistence Lecture 13 [Local Stores]
  *
  * Level DB
  * - It is a file based key-value store [Written by google]
  * - Supports Compaction
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002460#overview
  */

object LocalStores extends App {

  class SimplePersistentActor extends PersistentActor with ActorLogging {

    // mutable state
    var nMessages = 0

    override def persistenceId: String = "simple-persist-actor"

    override def receiveCommand: Receive = {
      case "print" =>
        log.info(s"I have persisted $nMessages so far")

      case "snap" =>
        saveSnapshot(nMessages)

      case SaveSnapshotSuccess(metadata) => log.info(s"Save snapshot was Successful $metadata")

      case SaveSnapshotFailure(_, cause) => log.info(s"Failed with $cause")

      case message =>
        persist(message) { e =>
          log.info(s"Persisting $message")
          nMessages += 1
        }
    }

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, payload: Int) =>
        log.info(s"Recovered snapshot $payload")
        nMessages = payload

      case RecoveryCompleted =>
        log.info("RecoveryDone")

      case messages =>
        log.info(s"Recovered: $messages")
        nMessages += 1
    }
  }

  val system = ActorSystem("LocalStores", ConfigFactory.load("application-persistence.conf").getConfig("localStores"))
  val persistentActor = system.actorOf(Props[SimplePersistentActor], "persistentActor")

  for (i <- 1 to 10) {
    persistentActor ! s"I love Akka $i"
  }

  persistentActor ! "print"
  persistentActor ! "snap"

  for (i <- 11 to 20) {
    persistentActor ! s"I love Akka $i"
  }

}
