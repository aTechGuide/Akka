package persistence.primer

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}
import com.typesafe.config.ConfigFactory

/**
  * Persistence Lecture 11 [Recovery]
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002446
  */

object RecoveryDemo extends App {

  case class Command(contents: String)
  case class Event(contents: String)

  class RecoveryActor extends PersistentActor with ActorLogging {

    override def persistenceId: String = "recovery-actor"

    override def receiveCommand: Receive = {
      case Command(contents) =>
        persist(Event(contents)) { e =>
          log.info(s"Successfully persisted $e, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")
        }
    }

    override def receiveRecover: Receive = {

      case RecoveryCompleted =>
        // Can be used to do additional initialization post recovery
        log.info("I have finished recovering")

      case Event(contents) =>

        // Simulating Failure
       //  if (contents.contains("314")) throw new RuntimeException("Failure")
        log.info(s"Recovered $contents, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("I failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

    // override def recovery: Recovery = Recovery(toSequenceNr = 100)
    // override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
//    override def recovery: Recovery = Recovery.none

  }

  val system = ActorSystem("PersistentActorsExercise", ConfigFactory.load("application-persistence.conf"))
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recoveryActor")

  /*
    1. Stashing of Commands

    // ALL COMMANDS SENT DURING RECOVERY ARE STASHED
   */

  for (i <- 1 to 1000) {
    // recoveryActor ! Command(s"command $i")
  }

  /*
    2 - Failure during Recovery
    - `onRecoveryFailure` will be called
    - And the actor will be stopped
   */

  /*
    3 - Customizing Recovery
    - Please do not persist events after a customized INCOMPLETE recovery
   */

  /*
    4
    - Recovery Status [Knowing when you are done recovering]
    - Receiving a signal when we're done recovering
   */

  /*
    5 - Stateless Actors
   */

  // Demo

  case class Event2(id: Int, contents: String)

  class RecoveryActor2 extends PersistentActor with ActorLogging {

    override def persistenceId: String = "recovery-actor-2"

    override def receiveCommand: Receive = online(0)

    def online(latestPersistedEventId: Int): Receive = {
      case Command(contents) =>
        persist(Event2(latestPersistedEventId, contents)) { e =>
          log.info(s"Successfully persisted $e, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")

          context.become(online(latestPersistedEventId + 1))

        }
    }

    override def receiveRecover: Receive = {

      case Event2(id, contents) =>

        log.info(s"Recovered $contents, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")

        context.become(online(id + 1)) // <- this will not change the Event handler used during the recovery
      // the `receiveRecover` method will always always always be used during recovery regardless of how many `context.become` we put inside.

      // After recovery the "Normal" handler will be the result of ALL the stacking of context.becomes
    }

  }

  val recoveryActor2 = system.actorOf(Props[RecoveryActor2], "recoveryActor2")

  for (i <- 1 to 1000) {
    //recoveryActor2 ! Command(s"command $i")
  }

  recoveryActor2 ! Command(s"Special Command 1")
  recoveryActor2 ! Command(s"Special Command 2")

}
