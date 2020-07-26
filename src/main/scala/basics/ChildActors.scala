package basics

import akka.actor.{Actor, ActorRef, ActorSelection, ActorSystem, Props}
import basics.ChildActors.CreditCard.{AttachToAccount, CheckStatus}

/**
  * Lecture 15 [Child Actors]
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418650#overview
  */

object ChildActors extends App {

  object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }

  class Parent extends Actor {
    import Parent._

    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} Creating Child")

        // Actors can create other actors by invoking `context.actorOf()`
        val childRef  = context.actorOf(Props[Child], name)
        context.become(withChild(childRef))

    }

    def withChild(child: ActorRef): Receive = {

      case TellChild(message) => child forward message
    }


  }

  class Child extends Actor {
    override def receive: Receive = {
      case message: String => println(s"${self.path} I got: $message")
    }
  }

  val system = ActorSystem("ParentChildDemo")
  val parent = system.actorOf(Props[Parent], "parent")

  import Parent._

  parent ! CreateChild("child") // akka://ParentChildDemo/user/parent Creating Child
  parent ! TellChild("Hey Kid !!") // akka://ParentChildDemo/user/parent/child I got: Hey Kid !!

  /**
    * Now this whole child creation feature gives Akka the ability to create actor hierarchy's
    *
    * Parent -> Child 1 -> Child 2 -> .....
    *        -> Child 3 -> GrandChild
    *
    * Child is owned by parents but who owns parent is parent some kind of top level actor or something.
    * And the answer is NO.
    *
    * We have what we call Guardian actors or top level actors every AKka actors system has three Guardian Actors
    * 1 /system
    *   - System Guardian
    *   - Every actor system has its own actors for managing various things and for example managing the logging system
    *   - The logging system in AKka is also implemented using actors and  `/system` manages all these system Actors
    *
    * 2 /user
    *   - User Level Guardian
    *   - Every actor that We create using `system.actorOf` are actually owned by this `/user` which explains `akka://<actor_system_name>/user/<actor_name>` path.
    *
    * 3 /
    *   - Root Guardian
    *   - The root Guardian manages both the system Guardian and the user level guardian, so the system and user sit below the root Guardian
    *   - The root Guardian sits at the level of the actor system itself.
    *   - So if Root Guardian throws an exception or dies in some other way the whole actor system is brought down.
    */

  /**
    * Actor Selection
    * - Finding the Actor By Path
    * - If the path happens to be invalid then actor selection object will contain no actor and any message that we send to that will be dropped.
    *   - INFO message will be printed indicating message delivered to Dead Letter
    *
    */

  val childSelection: ActorSelection = system.actorSelection("/user/parent/child")

  childSelection ! "I found you" // akka://ParentChildDemo/user/parent/child I got: I found you

  /**
    * DANGER
    *
    * Never Pass Mutable Actor State Or `this` reference to Child Actors
    * - This has the danger of breaking actor encapsulation because the child actor suddenly has access to the
    *   internals of the parent actor so he can mutate the state or directly call methods of the parent actor without Sending the message
    *   And this breaks are very sacred actor principles.
    *
    * - This problem is a huge one and it extends to both the `this` reference and any `mutable state` of an actor.
    *   This is called CLOSING OVER mutable state or `this` reference. Scala doesn't prevent this at compile time
    *   so it's our job to make sure we keep the actor encapsulation.
    */

  object NaiveBankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)

    case object InitializeAccount

  }
  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._

    var amount = 0
    override def receive: Receive = {
      case InitializeAccount =>
        val creditCardRef = context.actorOf(Props[CreditCard], "card")
        creditCardRef ! AttachToAccount(this)
      case Deposit(funds) => depositFunds(funds)
      case Withdraw(funds) => withdrawFunds(funds)
    }

    def depositFunds(funds: Int): Unit = amount += funds
    def withdrawFunds(funds: Int): Unit = amount -= funds
  }

  object CreditCard {
    case class AttachToAccount(bankAccount: NaiveBankAccount) //[DANGER] Instead of an actor reference this message contains an instance of an actual actor JVM object.
    case object CheckStatus
  }
  class CreditCard extends Actor {

    override def receive: Receive = {
      case AttachToAccount(account) => context.become(attachTo(account))
    }

    def attachTo(account: NaiveBankAccount): Receive = {

      case CheckStatus => println(s"${self.path} Your messge has been processed")
        account.withdrawFunds(1) // Because I can and That's THE PROBLEM [Illegitimate Withdraw]
    }
  }

  import NaiveBankAccount._
  import CreditCard._

  val bankAccountRef = system.actorOf(Props[NaiveBankAccount], "account")
  bankAccountRef ! InitializeAccount
  bankAccountRef ! Deposit(100)

  Thread.sleep(500)
  val ccSelection = system.actorSelection("/user/account/card")
  ccSelection ! CheckStatus




}
