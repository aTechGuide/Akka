package testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._
import scala.util.Random

/**
  * Lecture 19 [Intro to TestKit]
  *
  * Who is sending the messages to the Actor class that we are testing ?
  * - The answer is the `testActor` which is a member of testKit and is an actor used for communication with the actors that we want to test.
  * - Now test actor in particular for our tests is also passed implicitly as the sender of every single message that we send because we mixed in the `ImplicitSender` trait.
  *   So this is what the implicit sender trait is doing it's passing tests after as the implicit sender for every single message.
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418660#overview
  */

class BasicSpec extends TestKit(ActorSystem("BasicSpec"))
  with ImplicitSender // which is used for a send reply scenarios in actors
  with WordSpecLike //   This allows the description of tests in a very natural language style BDD (Behavior Driven Testing)
  with BeforeAndAfterAll { // Supplies Hooks

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import BasicSpec._
  "A simple actor" should {
    "send back the same message" in {
      val echoActor = system.actorOf(Props[SimpleActor])
      val message = "Hello Test"
      echoActor ! message

      expectMsg(message) // akka.test.single-expect-default
    }
  }

  "A Blackhole actor" should {
    "send back some message" in {
      val echoActor = system.actorOf(Props[Blackhole])
      val message = "Hello Test"
      echoActor ! message

      expectNoMessage(1 second)
    }
  }

  "A LabTest actor" should {

    val labTestActor = system.actorOf(Props[LabTestActor])

    "turn a string in upper case" in {

      val message = "I live Akka"
      labTestActor ! message

      val reply = expectMsgType[String]

      assert(reply == message.toUpperCase)
    }

    "reply to a greeting" in {

      val message = "greeting"
      labTestActor ! message

      expectMsgAnyOf("hi", "hello")
    }

    "reply to a favoriteTech" in {

      val message = "favoriteTech"
      labTestActor ! message

      expectMsgAllOf("scala" , "Akka")
    }

    "reply to a favoriteTech in a different way" in {

      val message = "favoriteTech"
      labTestActor ! message

      val messages = receiveN(2) // Seq[Any]


      // free to do complicated assertions
    }

    "reply to a favoriteTech in a fancy way" in {

      val message = "favoriteTech"
      labTestActor ! message

      expectMsgPF() {
        case "scala" => // Only care that Partial Function is define
        case "Akka" =>
      }

      // free to do complicated assertions
    }
  }



}

object BasicSpec {

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case message => sender() ! message
    }
  }

  class Blackhole extends Actor {
    override def receive: Receive = {
      case message =>
    }
  }

  class LabTestActor extends Actor {
    val random = new Random()
    override def receive: Receive = {
      case "greeting" =>
        if(random.nextBoolean) sender() ! "hi" else sender() ! "hello"
      case "favoriteTech" =>
        sender() ! "scala"
        sender() ! "Akka"
      case message: String => sender() ! message.toUpperCase
    }
  }


}
