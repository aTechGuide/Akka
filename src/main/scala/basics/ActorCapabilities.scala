package basics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorCapabilities extends App {

  class SimpleActor extends Actor {

    override def receive: Receive = {

      case "Hi!" => context.sender() ! "Hello, there" // `context.sender()` returns an ActorRef which we can use to send message back
      case message: String => println(s"${context.self} [Simple Actor] I have received $message") //  `context.self` === `self`
      case number: Int => println(s"$self [Simple Actor] I have received a number: $number")
      case SpecialMessage(contents) => println(s"[Simple Actor] I have received a SpecialMessage with content: $contents")

      //2
      case SendMessageToYourself(contents) => self ! contents

      // 3
      case SayHiTo(ref) => ref ! "Hi!" // Alice is being passed as "sender"

      // 4
      case WirelessPhoneMessage(content, ref) => ref forward (content + "s") // keep the original sender of WirelessPhoneMessage
    }
  }

  val system = ActorSystem("actorCapabilitiesDemo")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  simpleActor ! "hello, actor"

  // 1 - Messages can be of any Type
    // 1a - Message must be IMMUTABLE
    // 2b - Messages must be SERIALIZABLE
  simpleActor ! 42

  // In practice use case classes and case objects
  case class SpecialMessage(contents: String)
  simpleActor ! SpecialMessage("Special Content")

  // 2 Actors have information about their context i.e. `context` object and about themselves i.e. `context.self` === `this` (in OOP)
  case class SendMessageToYourself(content: String)
  simpleActor ! SendMessageToYourself("I am an actor and I'm proud of it")

  // 3 Actors can Reply to messages
  val alice = system.actorOf(Props[SimpleActor], "alice")
  val bob = system.actorOf(Props[SimpleActor], "bob")


  case class SayHiTo(ref: ActorRef)

  alice ! SayHiTo(bob)

  // 4 Dead Letters
  alice ! "Hi!" // i.e. reply to me (explicit value passed is null). So message will be sent to `deadLetters` which is a fake actor which receives messages not sent to anyone

  // 5 Forwarding Messages i.e. Alice forwarding message to Bob while keeping original sender
  case class WirelessPhoneMessage(content: String, ref: ActorRef)
  alice ! WirelessPhoneMessage("Hi", bob)



  /*
    Exercise 1: Implement a counter Actor
   */


  // Best practice to create Messages in Companion object of Actor that supports them
  // DOMAIN of the counter
  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }

  class Counter() extends Actor {

    import Counter._
    var counter: Int = 0

    override def receive: Receive = {

      case Increment => counter +=  1
      case Decrement => counter -=  1
      case Print => println("Value of count for CounterActor is : " + counter)
    }
  }

  import Counter._
  val counterActor = system.actorOf(Props[Counter], "Counter1")

  counterActor ! Increment
  counterActor ! Print

  (1 to 5).foreach(_ => counterActor ! Increment)
  (1 to 3).foreach(_ => counterActor ! Decrement)

  counterActor ! Print

  /*
    Exercise 2: A Bank account as an Actor
    Receives
      - Deposit Amount
      - Withdraw Amount
      - Statement
    Replies
      - Success
      - Failure
   */

  object BankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object Statement

    case class TransactionSuccess(message: String)
    case class TransactionFailure(message: String)
  }


  class BankAccount extends Actor {

    import BankAccount._

    var funds = 0

    override def receive: Receive = {
      case Deposit(amount) =>
        if (amount < 0) sender() ! TransactionFailure("Invalid Deposit Amount")
        else {
          funds +=amount
          sender() ! TransactionSuccess(s"Successfully Deposited $amount")
        }
      case Withdraw(amount) =>
        if (amount < 0) sender() ! TransactionFailure("Invalid Withdraw Amount")
        else if (amount > funds) sender() ! TransactionFailure("Insufficient funds")
        else {
          funds -=amount
          sender() ! TransactionSuccess(s"Successfully Withdraw $amount ")
        }
      case Statement => sender() ! s"Your balance is $funds"
    }
  }

  object Person {
    case class LiveTheLife(account: ActorRef)
  }

  class Person extends Actor {

    import Person._
    import BankAccount._

    override def receive: Receive = {
      case LiveTheLife(account) =>
        account ! Deposit(10000)
        account ! Withdraw(900000)
        account ! Withdraw(500)
        account ! Statement
      case message => println(message.toString)
    }
  }

  val account = system.actorOf(Props[BankAccount], "BankAccount")
  val person = system.actorOf(Props[Person], "Billionaire")

  import Person._
  person ! LiveTheLife(account)

}
