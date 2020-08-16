package persistence.stores

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

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