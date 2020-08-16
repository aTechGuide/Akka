package faultTolerance

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}

/**
  * Lecture 25 [Actor Lifecycle]
  * - In Notes
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418680#notes
  */

object ActorLifecycle extends App {

  object StartChild

  class LifeCycleActor extends Actor with ActorLogging {

    override def preStart(): Unit = log.info("I am Starting")
    override def postStop(): Unit = log.info("I have stopped")

    override def receive: Receive = {
      case StartChild =>
        context.actorOf(Props[LifeCycleActor], "child")
    }
  }

  val system = ActorSystem("LifecycleDemo")
  //val parent = system.actorOf(Props[LifeCycleActor], "parent") // [LifecycleDemo-akka.actor.default-dispatcher-2] [akka://LifecycleDemo/user/parent] I am Starting

//  parent ! StartChild // [LifecycleDemo-akka.actor.default-dispatcher-4] [akka://LifecycleDemo/user/parent/child] I am Starting
//  parent ! PoisonPill
  // [akka://LifecycleDemo/user/parent/child] I have stopped
  // [akka://LifecycleDemo/user/parent] I have stopped

  // We see first child is stopped

  /**
    * Restart
    */

  object Fail
  object FailChild
  object CheckChild
  object Check

  class Parent extends Actor with ActorLogging {

    private val child = context.actorOf(Props[Child], "supervisedChild")

    override def receive: Receive = {
      case FailChild => child ! Fail
      case CheckChild => child ! Check

    }
  }

  class Child extends Actor with ActorLogging {

    override def preStart(): Unit = log.info("Supervised Child Started")
    override def postStop(): Unit = log.info("Supervised Child Stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = log.info(s"Supervised Actor restarting because of ${reason.getMessage}")
    override def postRestart(reason: Throwable): Unit = log.info("Supervised Actor restarted")

    override def receive: Receive = {
      case Fail =>
        log.warning("Child will fail now")
        throw new RuntimeException("I Failed")

      case Check =>
        log.info("Alive and Kicking")
    }
  }

  val supervisor = system.actorOf(Props[Parent], "supervisor") // [akka://LifecycleDemo/user/supervisor/supervisedChild] Supervised Child Started

  supervisor ! FailChild
  // So the child actor will be restarted as a result of this exception.
  // Not only that but it's probably able to process more messages.

  supervisor ! CheckChild // Alive and Kicking

  // So even if the child actor threw an exception previously which is a very serious thing the child was still restarted
  // and it was able to process more messages.

  // And this is a part of the default supervision strategy
  // The default supervision strategy says the following,
  // If an actor threw an exception while processing a message this message which caused the exception to be thrown is removed from the queue
  // and not put back in the mailbox again. And the Actor is restarted which means the mailbox is untouched.

}
