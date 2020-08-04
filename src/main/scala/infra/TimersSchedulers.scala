package infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props, Timers}

import scala.concurrent.duration._

/**
  * Lecture 28 [Schedulers and Timers]
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418714#notes
  */

object TimersSchedulers extends App {

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("TimersSchedulers")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  system.log.info("Scheduling Reminder for simple Actor")

  system.scheduler.scheduleOnce(2 second) {
    simpleActor ! "reminder"
  } (system.dispatcher) // `system.dispatcher` is also an execution context [Method 1]

  import system.dispatcher // [Method 2]
  val routine: Cancellable = system.scheduler.schedule(1 second, 2 seconds) {
    simpleActor ! "heartbeat"
  } // <- Here it needs system.dispatcher

  system.scheduler.scheduleOnce(5 seconds) {
    routine.cancel
  }

  /**
    * Exercise
    */

  // Implement self closing Actor
  // If an Actor receive a message we have 1 second to send it another message.
  // If the time window expires the actor will stop itself.
  // If we send another message the time window is reset and you have one more second to send another message.
  // And so on and so forth.

  class SelfClosing extends Actor with ActorLogging {

    var schedule = createTimeoutWindow()
    def createTimeoutWindow(): Cancellable = {
      context.system.scheduler.scheduleOnce(1 second) {
        self ! "timeout"
      }
    }

    override def receive: Receive = {
      case "timeout" =>
        log.info("Stopping Myself")
        context.stop(self)
      case message =>
        log.info(s"Received $message, Staying Alive")
        schedule.cancel()
        schedule = createTimeoutWindow()

    }
  }

  val selfClosing = system.actorOf(Props[SelfClosing], "selfClosing")
  system.scheduler.scheduleOnce(250 millis) {
    selfClosing ! "ping"
  }

  system.scheduler.scheduleOnce(2 seconds) {
    system.log.info("Sending pong to the self closing actor")
    selfClosing ! "pong" // <- Went to dead letter
  }

  /**
    * Sending Messages to yourself. Akka provides utility specifically for this use case. Known as TIMER
    *
    * Timer
    * - Timers are a simpler and safer way to schedule messages to self from within an actor
    */

  object TimerBasedHeartBeatActor {
    case object TimerKey
    case object Start
    case object Reminder
    case object Stop
  }
  class TimerBasedHeartBeatActor extends Actor with ActorLogging with Timers {
    import TimerBasedHeartBeatActor._

    // This key is just used for the comparison of timers.
    // There can only be one timer for a given timer key and the timer keys are very weird in that they are not strings or integers or any kind of identifiers that can be any object
    // so we naturally create case objects for them.
    timers.startSingleTimer(TimerKey, Start, 500 millis)

    override def receive: Receive = {
      case Start =>
        log.info("Bootstrapping")
        timers.startPeriodicTimer(TimerKey, Reminder, 1 second)
        // when We do `timers.startPeriodicTimer` with the same timer key that was associated to another timer
        // the previous timer is canceled because we don't need to concern ourselves with the lifecycle of a timer. The timer is automatically canceled.

      case Reminder =>
        log.info("I am Alive")

      case Stop =>
        log.warning("Stopping")
        timers.cancel(TimerKey) // This will free up the timer associated to this timer key
        context.stop(self)
    }
  }

  val timerBasedHeartBeatActor = system.actorOf(Props[TimerBasedHeartBeatActor], "timerBasedHeartBeatActor")
  system.scheduler.scheduleOnce(5 seconds) {
    import TimerBasedHeartBeatActor._

    timerBasedHeartBeatActor ! Stop
  }

}
