package persistence.primer

import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import com.typesafe.config.ConfigFactory

/**
  * Persistence Lecture 6, 7 [Persistence Actors]
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002418s
  */

object PersistentActors extends App {

  /*
    Scenario
    - Let's say we have an accountant that keeps track of our invoices for our business and sums up the total income.
   */

  // EVENTS
  case class Invoice(recipient: String, date: Date, amount: Int)

  // COMMANDS
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)
  case class InvoiceBulk(invoices: List[Invoice])

  // Special COMMAND
  case object Shutdown

  class Accountant extends PersistentActor with ActorLogging {

    var latestInvoiceID = 0
    var totalAmount = 0

    // Persistence I.D. is how the events persisted by this actor will be identified in a persistent store. Best Practice Unique Per Actor
    override def persistenceId: String = "simple-accountant"

    // Normal Receive Method
    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        /*
          When you receive a command
          1) Create an EVENT to persist into the store
          2) you persisted that event and pass in a callback that will get triggered once the event is written
          3) Update the Actor State When the event has persisted
         */
        log.info(s"Received invoice for amount $amount ")
        val event = InvoiceRecorded(latestInvoiceID, recipient, date, amount)

        // `persist` is non blocking + Async
        // `persist` will be executed at some point in the future.
        // And the handler will also be executed at some point in the future after the event has successfully being persisted into the Journal.

        // Now normally we would know from the Akka Essentials never to access mutable state or call methods in async callbacks because we know that this can break the actor encapsulation.
        // But here it's OK you don't have race conditions. So safe to access mutable state here.
        // Akka persistence guarantees that no other threads are accessing the actor during a callback. Behind the scenes aka persistence is also message based.

        // Time Gap Explanation
        // Since there is a gap of time between persisting and handling the event. What if other messages are handled in the meantime between persisting and the callback.
        // What if other commands are being sent in the meantime.
        // Now that will be an excellent question because with `persist` Akka guarantees that all the messages between persisting and the handling of the persisted event are stashed.
        persist(event) /* Time gap: All other messages sent to this actor are stashed */ { e =>
          latestInvoiceID += 1
          totalAmount += amount

          // We can correctly identify the sender of the command.
          sender() ! "Persistence Ack"

          log.info(s"Persisted $e as invoice #${e.id}, for total amount $totalAmount")
        }

      // We can also act like a normal actor and are not obliged to Persist an Event
      case "print" => log.info(s"Latest invoice id: $latestInvoiceID ")

      case InvoiceBulk(invoices) =>
        val invoiceIds = latestInvoiceID to (latestInvoiceID + invoices.size)
        val events = invoices.zip(invoiceIds).map { pair =>
          val id = pair._2
          val invoice = pair._1

          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }

        // `persistAll` actually persist each event in sequence and callback is executed after each event has been successfully written to the Journal.
        persistAll(events) { e =>
          latestInvoiceID += 1
          totalAmount += e.amount

          log.info(s"Persisted $e as invoice #${e.id}, for total amount $totalAmount")
        }

      // The difference between shutdown and a poisonPill is that shut down will be put into the normal mailbox that guarantees in the case that I send this shutdown message after the invoices
      // that the shutdown message will be handled after all the invoices have been correctly treated
      case Shutdown =>
        context.stop(self)


    }

    // It is the handler that's being called on recovery
    // So on recovery the actor will query the persistent store for all the events associated to this persistence I.D.
    // and all the events will be replayed by this actor in the exact order that they were originally written and
    // they will be sent to this actor as simple messages and this receive recover handler will handle them.
    override def receiveRecover: Receive = {

      /*
        Best Practice
        - Follow the logic in the persist steps of receive command
       */
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceID = id
        totalAmount += amount
        log.info(s"Recovered invoice #$id for amount = $amount, total amount = $totalAmount ")

    }

    // Called when `persist(event)` fails and The actor will be STOPPED regardless of supervision strategy
    // The reason is that since we don't know if the event was persisted or not the actor is in an inconsistent state.
    // So it cannot be trusted even if it's resumed.
    // When this happens it's a good idea to start the actor again after a while in the back off supervisor pattern is useful here
    // Best practice -> Start the actor again after a while (Use back off supervisor)
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      // `seqNr` is Sequence Number of event from Journals point of view
      log.error(s"Fail to persist $event because of $cause ")
      super.onPersistFailure(cause, event, seqNr)
    }


    // This method will be called if the Journal throws an exception while persisting the event
    // In this case the actor is RESUMED not STOPPED because we know for sure that the event was not persisted.
    // And so the actress state was not corrupted in the process
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected for $event because of $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistentActors", ConfigFactory.load("application-persistance.conf"))
  val accountant = system.actorOf(Props[Accountant], "accountant")

  for (i <- 1 to 10) {
    // accountant ! Invoice("Sofa Company", new Date, i * 1000)
  }

  // `persistAll`
  val newInvoices = for (i <- 1 to 5) yield Invoice("Chairs", new Date, i * 2000)
  // accountant ! InvoiceBulk(newInvoices.toList)

  /*
    NEVER EVER CALL PERSIST OR PERSIST ALL FROM FUTURES

    otherwise we risk breaking the actor encapsulation because the actor thread is free to process messages while you're persisting.
    And if the normal actor thread also calls persist you suddenly have two threads persisting events simultaneously.
    Because the event order is non deterministic we risk corrupting the actor   state.
   */

  /*
    Shutdown of Persistent Actors

    Best Practice -> Define our own Shutdown Message
   */
  accountant ! Shutdown


}
