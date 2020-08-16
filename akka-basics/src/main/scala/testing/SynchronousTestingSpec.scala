package testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, TestActorRef, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.Duration

/**
  * Lecture 23 [Synchronous Testing]
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418670#overview
  */

class SynchronousTestingSpec extends  WordSpecLike with BeforeAndAfterAll {

  implicit val system = ActorSystem("SynchronousTestingSpec")

  override def afterAll(): Unit = {
    system.terminate()
  }

  import SynchronousTestingSpec._
  "A counter" should {
    "synchronously increase its counter" in {
      val counter = TestActorRef[Counter](Props[Counter])

      counter ! Inc // counter has ALREADY received the message because sending a message to a `TestActorRef` happens in the calling thread.

      // At this point I can make assertions on this counter
      assert(counter.underlyingActor.count == 1)

      // `TestActorRef`only work in the calling thread so when you send a message to it.
      // The thread will only return after the message has been processed.
      // So after that you can do assertions.

    }

    "synchronously increase its counter at the call of the rcieve function " in {
      val counter = TestActorRef[Counter](Props[Counter])
      counter.receive(Inc)

      assert(counter.underlyingActor.count == 1)
    }

    "work on the calling thread dispatcher" in {
      val counter = system.actorOf(Props[Counter].withDispatcher(CallingThreadDispatcher.Id))
      val probe = TestProbe()

      probe.send(counter, Read) //<- This interaction has already happened
      probe.expectMsg(Duration.Zero, 0) // Probe has already received the message `0` because the counter operates on the calling thread.
    }

  }

}

object SynchronousTestingSpec {

  case object Inc
  case object Read

  class Counter extends Actor {
    var count = 0
    override def receive: Receive = {
      case Inc => count +=1
      case Read => sender() ! count
    }
  }
}