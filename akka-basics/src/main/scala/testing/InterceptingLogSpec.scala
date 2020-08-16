package testing

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

/**
  * Lecture 22 [Intercepting Logs]
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418666#overview
  */

class InterceptingLogSpec extends TestKit(ActorSystem("InterceptingLogSpec", ConfigFactory.load().getConfig("interceptingLogMessages")))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import InterceptingLogSpec._
  "A checkout flow" should {

    val item = "Rock the JVM Akka Course"
    val creditCard = "1234-1234-1234-1234"
    val invalidCreditCard = "0000-0000-0000-0000"

    "correctly log the dispatch of an order" in {

      // This creates an event filter object which scans for log messages at level `info`
      // and we can pass some parameters for e.g. exact message that we're looking for OR a pattern OR the number of occurrences for these messages and so on and so forth.
      EventFilter.info(pattern = s"Order [0-9]+ for item $item has been dispatched", occurrences = 1) intercept {
        val checkOut = system.actorOf(Props[CheckoutActor])
        checkOut ! Checkout(item, creditCard)
      }
    }

    "exception if payment is denied" in {
      EventFilter[RuntimeException](occurrences = 1) intercept {
        val checkOut = system.actorOf(Props[CheckoutActor])
        checkOut ! Checkout(item, invalidCreditCard)
      }
    }
  }


}

object InterceptingLogSpec {
  case class Checkout(item: String, creditCard: String)
  case class AuthorizeCard(creditCard: String)
  case class DispatchOrder(item: String)

  case object PaymentAccepted
  case object PaymentDenied
  case object OrderConfirmed

  class CheckoutActor extends Actor {
    private val paymentManager = context.actorOf(Props[PaymentManager])
    private val fulfillmentManager = context.actorOf(Props[FulfillmentManager])

    override def receive: Receive = awaitingCheckout

    def awaitingCheckout: Receive = {
      case Checkout(item, card) =>
        paymentManager ! AuthorizeCard(card)
        context.become(pendingPayment(item))
    }

    def pendingPayment(item: String): Receive = {
      case PaymentAccepted => fulfillmentManager ! DispatchOrder(item)
        context.become(pendingFulfillment(item))
      case PaymentDenied =>
        throw new RuntimeException("Error")

    }

    def pendingFulfillment(item: String): Receive = {
      case OrderConfirmed => context.become(awaitingCheckout)
    }

  }

  class PaymentManager extends Actor {
    override def receive: Receive = {
      case AuthorizeCard(card) =>
        if (card.startsWith("0")) sender() ! PaymentDenied
        else {
          Thread.sleep(4000)
          sender() ! PaymentAccepted
        }
    }
  }

  class FulfillmentManager extends Actor with ActorLogging {
    var orderID = 0
    override def receive: Receive = {
      case DispatchOrder(item) =>
        orderID += 1
        log.info(s"Order $orderID for item $item has been dispatched")
        sender() ! OrderConfirmed
    }
  }

}
