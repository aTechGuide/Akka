package testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

/**
  * Lecture 20 [Test Probes]
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418662#overview
  */

class TestProbSpec extends TestKit(ActorSystem("TestProbSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TestProbSpec._

  "A master actor" should {
    "register a slave" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave") // Test probe is a special actor with some assertion capabilities.

      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
    }

    "send the work to slave actor" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workLoadString = "I Love Akka"
      master ! Work(workLoadString)

      // the interaction between master and slave
      slave.expectMsg(SlaveWork(workLoadString, testActor)) // `testActor` is the member of testKit which is implicitly passed as the sender of every single message
      slave.reply(WorkCompleted(3, testActor))

      expectMsg(Report(3))

    }

    "aggregate data correctly" in {

      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workLoadString = "I Love Akka"
      master ! Work(workLoadString)
      master ! Work(workLoadString)

      // In meantime I do NOT have a slave actor
      slave.receiveWhile() {
        case SlaveWork(`workLoadString`, `testActor`) => slave.reply(WorkCompleted(3, testActor))
      }
      expectMsg(Report(3))
      expectMsg(Report(6))

    }
  }

}

object TestProbSpec {

  /*
    So the flow goes like this.
    - We send some work to the master
    - The master sends the slave the piece of work
    - Now the slave processes the work and replies to master
    - Master aggregates the results.
    - Master then sends the total word count to the original requester
   */

  case class Register(slaveRef: ActorRef )
  case class Work(text: String)
  case class SlaveWork(text: String, originalRequester: ActorRef)
  case class WorkCompleted(count: Int, originalRequester: ActorRef)
  case class Report(totalWordCount: Int)
  case object RegistrationAck

  class Master extends Actor {
    override def receive: Receive = {
      case Register(slaveRef) =>
        sender() ! RegistrationAck
        context.become(online(slaveRef, 0))

      case _ =>
    }

    def online(slaveRef: ActorRef, totalWordCount: Int): Receive = {

      case Work(text) => slaveRef ! SlaveWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newTotalWordCount = totalWordCount + count
        originalRequester ! Report(newTotalWordCount)

        context.become(online(slaveRef, newTotalWordCount))

    }
  }

  // class Slave extends Actor ....

}
