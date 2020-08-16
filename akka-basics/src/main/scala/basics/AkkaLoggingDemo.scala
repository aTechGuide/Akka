package basics

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

/**
  * Lecture 17 [Actor Logging]
  *
  * - Logging is done asynchronously to minimize performance impact.
  * - Akka logging is done with Actors
  * - We can change the logger e.g. SLF4J
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418654#overview
  */

object AkkaLoggingDemo extends App {

  // Method 1: Explicit Logging
  class SimpleActorWithExplicitLogger extends Actor {
    val logger = Logging(context.system, this)

    override def receive: Receive = {
      case message => logger.info(message.toString)
    }
  }

  val system = ActorSystem("LoggingDemo")
  val actor = system.actorOf(Props[SimpleActorWithExplicitLogger])

  actor ! "Please log this"

  // Method 1: Actor Logging
  class SimpleActorWithLogging extends Actor with ActorLogging {

    override def receive: Receive = {
      case (a, b) => log.info("Two things: {} and {}", a, b)
      case message: String => log.info(message.toString)
    }
  }

  val actor2 = system.actorOf(Props[SimpleActorWithLogging])
  actor2 ! "Please log this with trait extended"
  actor2 ! (2, 3)

}
