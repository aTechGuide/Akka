package faultTolerance

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, ActorSystem, AllForOneStrategy, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

/**
  * Lecture 26 [Actor Supervision]
  *
  * In Notes
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418682
  */

class SupervisionSpec extends TestKit(ActorSystem("SupervisionSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import SupervisionSpec._

  "A supervisor" should {
    "resume its child in case of a minor fault" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I love Akka"
      child ! Report
      expectMsg(3)

      child ! "Akka is awesome because I am learning to think in a whole new way"
      child ! Report
      expectMsg(3)
    }

    "restart its child in case of Empty sentence" in {

      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I love Akka"
      child ! Report
      expectMsg(3)

      child ! ""
      child ! Report
      expectMsg(0)
    }

    "terminate its child in case of Major Error" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)
      child ! "akka is nice"

      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)
    }

    "escalate an error when it doesn't know what to do " in {

      val supervisor = system.actorOf(Props[Supervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)
      child ! 43

      // When an actor escalates, it stops all its children and escalates the failure to the parent.
      // So I should expect this fuzzy word counter to be dead by the time the supervisor triggers the or escalates these exception to its parent.
      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)

      // The `Escalate` strategy escalates the exception to the user guardian
      // and user guardian's default strategy is to restart everything.
      // The supervisors restart method will kill the fuzzy word counter.

    }
  }

  "A kinder supervisor" should {
    "not kill children in case it's restarted or escalated failures" in {

      val supervisor = system.actorOf(Props[NoDeathOnRestartSupervisor], "NoDeathOnRestartSupervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "Akka is cool"
      child ! Report

      expectMsg(3)

      child ! 45
      // So in the case of exception, the directive is escalate.
      // So `NoDeathOnRestartSupervisor` escalates to the user guardian.
      // The user guardian will restart this supervisor.
      // But on restart, I won't kill the child.
      // So I'm going to expect this child to still be alive if I send a report to it.

      child ! Report
      // Because the user guardian restarts everything.
      // I'm expecting this child to be restarted as well.

      expectMsg(0)


    }
  }

  "An All For One supervisor" should {
    "apply the all for one strategy" in {
      val supervisor = system.actorOf(Props[AllForOneSupervisor], "AllForOneSupervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      supervisor ! Props[FussyWordCounter]
      val secondChild = expectMsgType[ActorRef]

      secondChild ! "Testing supervision"
      secondChild ! Report
      expectMsg(2)

      EventFilter[NullPointerException]() intercept {
        child ! ""
      }

      Thread.sleep(500)
      secondChild ! Report
      expectMsg(0)

    }
  }

}

object SupervisionSpec {

  case object Report

  class Supervisor extends Actor {

    override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {

      // We are overriding Supervision Strategy based on the exceptions that child actors can throw

      case _: NullPointerException => Restart // Restarting the child
      case _: IllegalArgumentException => Stop // Stop the child
      case _: RuntimeException => Resume
      case _: Exception => Escalate
    }

    override def receive: Receive = {
      case props: Props =>
        val childRef = context.actorOf(props)
        sender() ! childRef
    }
  }

  class NoDeathOnRestartSupervisor extends Supervisor {
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      // super.preRestart(reason, message) <- This is the default behaviour
      // empty
    }
  }

  class AllForOneSupervisor extends Supervisor {
    override val supervisorStrategy: SupervisorStrategy = AllForOneStrategy() {
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException => Resume
      case _: Exception => Escalate
    }

    // The difference between OneForOneStrategy and AllForOneStrategy is that the OneForOneStrategy applies this decision on the exact actor that caused the failure,
    // whereas `AllForOneStrategy` applies this supervisor strategy for all the actors, regardless of the one that actually caused the failure.
    // So in case one of the children fails, with an exception, all of the children are subject to the same supervision directive.
  }

  class FussyWordCounter extends Actor {

    var words = 0

    override def receive: Receive = {
      case "" => throw new NullPointerException("Sentence is empty")
      case sentence: String =>
        if (sentence.length > 20) throw new RuntimeException("sentence is too big")
        else if (!Character.isUpperCase(sentence(0))) throw new IllegalArgumentException("sentence must start with uppercase")
        else words += sentence.split(" ").length
      case Report => sender() ! words
      case _ => throw new Exception("Can only receive strings")


    }
  }

}
