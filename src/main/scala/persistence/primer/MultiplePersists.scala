package persistence.primer

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor
import com.typesafe.config.ConfigFactory

/**
  * Persistence Lecture 9 [Multiple Persists]
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002442
  */

object MultiplePersists extends App {

  /*
    Diligent accountant
    - This actor will persist two events:
      - one is tax record for the fiscal authority
      - an invoice record for personal logs or some auditing authority

   */

  // COMMANDS
  case class Invoice(recipient: String, date: Date, amount: Int)

  // EVENTS
  case class TaxRecord(taxID: String, recordID: Int, date: Date, amount: Int)
  case class InvoiceRecord(invoideRecordID: Int, recipient: String, date: Date, amount: Int)


  object DiligentAccountant {
    def props(taxId: String, taxAuthority: ActorRef) = Props(new DiligentAccountant(taxId, taxAuthority))
  }

  class DiligentAccountant(taxId: String, taxAuthority: ActorRef) extends PersistentActor with ActorLogging {

    var latestRecordId = 0
    var latestInvoiceRecordId = 0

    override def persistenceId: String = "diligent-accountant"

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>

        // Imagine call to `persist` as journal ! TaxRecord
        persist(TaxRecord(taxId, latestRecordId, date, amount / 3)) { record =>

          taxAuthority ! record
          latestRecordId += 1

          persist("I hereby declare this tax record to be true and complete.") { declaration =>

            taxAuthority ! declaration
          }
        }

        // Imagine call to `persist` as journal ! InvoiceRecord
        persist(InvoiceRecord(latestInvoiceRecordId, recipient, date, amount)) { invoiceRecord =>

          taxAuthority ! invoiceRecord
          latestInvoiceRecordId += 1

          persist("I hereby declare this Invoice record to be true and complete.") { declaration =>

            taxAuthority ! declaration
          }
        }
    }

    override def receiveRecover: Receive = {
      case event => log.info(s"Recovered $event")
    }


  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Received $message")
    }
  }

  val system = ActorSystem("MultiplePersists", ConfigFactory.load("application-persistance.conf"))
  val taxAuthority = system.actorOf(Props[TaxAuthority], "HMRC")
  val accountant = system.actorOf(DiligentAccountant.props("UK42432 4323", taxAuthority), "accountant")

  accountant ! Invoice("Sofa Company", new Date, 2000)

  /**
    * PERSISTENCE IS ALSO BASED ON MESSAGE PASSING
    */

  /*

    The message ordering (TaxRecord -> InvoiceRecord) is GUARANTEED
    - Call to `persist` are executed in Order
    - And also the handlers for persist events are going to be called in order.
    - Nested `persist` is executed after the enclosing persist
   */


  // Following will be printed
  /*
    [akka://MultiplePersists/user/HMRC] Received TaxRecord(UK42432 4323,0,Wed Aug 12 13:13:42 IST 2020,666)
    [akka://MultiplePersists/user/HMRC] Received InvoiceRecord(0,Sofa Company,Wed Aug 12 13:13:42 IST 2020,2000)
    [akka://MultiplePersists/user/HMRC] Received I hereby declare this tax record to be true and complete.
    [akka://MultiplePersists/user/HMRC] Received I hereby declare this Invoice record to be true and complete.
   */

  accountant ! Invoice("Super Sofa Company", new Date, 2000)

  /*

  GUARANTEED -> Group 1 will come before Group 2

  // Group 1
    [akka://MultiplePersists/user/HMRC] Received TaxRecord(UK42432 4323,0,Wed Aug 12 13:16:54 IST 2020,666)
    [akka://MultiplePersists/user/HMRC] Received InvoiceRecord(0,Sofa Company,Wed Aug 12 13:16:54 IST 2020,2000)
    [akka://MultiplePersists/user/HMRC] Received I hereby declare this tax record to be true and complete.
    [akka://MultiplePersists/user/HMRC] Received I hereby declare this Invoice record to be true and complete.

  // Group 2
    [akka://MultiplePersists/user/HMRC] Received TaxRecord(UK42432 4323,1,Wed Aug 12 13:16:54 IST 2020,666)
    [akka://MultiplePersists/user/HMRC] Received InvoiceRecord(1,Super Sofa Company,Wed Aug 12 13:16:54 IST 2020,2000)
    [akka://MultiplePersists/user/HMRC] Received I hereby declare this tax record to be true and complete.
    [akka://MultiplePersists/user/HMRC] Received I hereby declare this Invoice record to be true and complete.
   */

}
