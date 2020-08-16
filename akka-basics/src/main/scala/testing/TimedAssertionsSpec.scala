package testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.util.Random
import scala.concurrent.duration._

/**
  * Lecture 21 [Timed Assertions]
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418664#overview
  */

class TimedAssertionsSpec extends TestKit(ActorSystem("TimedAssertionsSpec", ConfigFactory.load().getConfig("specialTimedAssertionsConfig") ))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TimedAssertionsSpec._

  "A Worker actor" should {

    val workerActor = system.actorOf(Props[Worker])
    "reply with the meaning of life in a timely manner" in {

      within(500.milli, 1.second) {
        workerActor ! "work"
        expectMsg(WorkResult(42))
      }
    }

    "reply with valid work at reasonable cadence" in {

      within( 1.second) {
        workerActor ! "workSequence"

        val results: Seq[Int] = receiveWhile[Int](max = 2.seconds, idle = 500.millis, messages = 10) {
          case WorkResult(result) => result
        }
        assert(results.sum > 5)
      }
    }

    "reply to a test probe in a timely manner" in {

      within( 1.second) { // within block has NO influence on TestProbe
        val probe = TestProbe()
        probe.send(workerActor, "work")
        probe.expectMsg(WorkResult(42)) // configured with timeout of 0.3 seconds in application.conf

      }
    }
  }

}

object TimedAssertionsSpec {

  case class WorkResult(result: Int)

  class Worker extends Actor {
    override def receive: Receive = {
      case "work" =>
        // Long Computation
      Thread.sleep(500)
        sender() ! WorkResult(42)

      case "workSequence" =>
        val r = new Random()
        for (_ <- 1 to 10) {
          Thread.sleep(r.nextInt(50))
          sender() ! WorkResult(1)
        }
    }

  }

}
