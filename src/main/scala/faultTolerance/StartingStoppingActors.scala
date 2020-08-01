package faultTolerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Kill, PoisonPill, Props, Terminated}

/**
  * Lecture 24 [Starting Stopping Actors]
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418672#overview
  */

object StartingStoppingActors extends App {

  val system = ActorSystem("StoppingActors")

  object Parent {
    case class StartChild(name: String)
    case class StopChild(name: String)

    case object Stop
  }

  class Parent extends Actor with ActorLogging {
    import Parent._

    override def receive: Receive = withChildren(Map())

    def withChildren(children: Map[String, ActorRef]): Receive = {
      case StartChild(name) =>
        log.info(s"Starting Child $name")
        context.become(withChildren(children + (name -> context.actorOf(Props[Child], name))))

      case StopChild(name) =>
        log.info(s"Stopping Child with name = $name")
        val childOption = children.get(name)

        childOption.foreach { childRef =>
          context.stop(childRef)
          // `context.stop` is nonblocking method.
          // So everything happens asynchronously when we say `context.stop` we basically send that signal to the child actor to stop.
          // That doesn't mean that it's stopped immediately.
        }

      case Stop =>
        log.info(s"Stopping Myself")
        context.stop(self)
        // The point here with `context.stop` is again that this is asynchronous but this also stops all of its child actors.
        // It stops all the children first and then it stops the parent

      case message => log.info(message.toString)
    }
  }

  class Child extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  import Parent._

  /**
    * Method 1 Using context.stop
    */
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! StartChild("child1")

  val child = system.actorSelection("/user/parent/child1")
  child ! "Hi Kid"

  parent ! StopChild("child1")

  // for (_ <- 1 to 50) child ! "are you still there"
  // So even after we've sent the StopChild signal. So parents saying stopping child with a name `child1`
  // The child actor still receives a few of above messages.

  // After a certain point the child1 stops and the messages are not being received.
  // Which means that the messages are being sent to that fictitious actor called Dead Letters.

  // So even after the child has received the stop signal it may still receive some messages before the actors system actually stops the child.

  parent ! StartChild("child2")
  val child2 = system.actorSelection("/user/parent/child2")

  child2 ! "Hi Second Child"

  parent ! Stop // The point is that the Child stops before the Parent actually stops.
  //for (_ <- 1 to 10) parent ! "Tell Parent, Are you still there" // Should not be received
  //for (i <- 1 to 100) child2 ! s"[$i] Second Kid, Are you still there"

  /**
    * Method 2 Using Special Messages
    */

  val looseActor = system.actorOf(Props[Child], "looseActor")
  looseActor ! "Hello Loose Actor"

  looseActor ! PoisonPill
  // PoisonPill is one of the special messages that are handled by actors
  // It will invoke the stopping procedure so that if I send another message to `looseActor` this message will likely be logged to dead letters.

  looseActor ! "looseActor, Are you still there" // Sent to Dead Letter

  // Abruptly Terminate Actor
  val abruptlyTerminatedActor = system.actorOf(Props[Child], "abruptlyTerminatedActor")
  abruptlyTerminatedActor ! "You are about to be terminated"
  abruptlyTerminatedActor ! Kill // [StoppingActors-akka.actor.default-dispatcher-4] [akka://StoppingActors/user/abruptlyTerminatedActor] Kill (akka.actor.ActorKilledException: Kill)
  abruptlyTerminatedActor ! "You have been terminated" // Sent to Dead Letter

  // So `Kill` and `PoisonPill` are special and they're handled separately by actors.
  // So we cannot handle them in your normal received message handler

  // Behind the scenes, There's actually a separate received message handler that Akka controls so we cannot catch a `PoisonPill` and ignore it for example.

  /**
    * Death Watch
    * - It is a mechanism for being notified when an actor dies.
    */

  class Watcher extends Actor with ActorLogging {
    import Parent._

    override def receive: Receive = {

      case StartChild(name) =>
        val child = context.actorOf(Props[Child], name)
        log.info(s"Started ans watching child named: $name")
        context.watch(child)
        // `context.watch(child)` registers Watcher actor for the death of the child
        // when the child dies Watcher actor will receive a special `Terminated` message

        // `context.watch(child)` is often used in conjunction with its opposite `context.unwatch(child)`.
        // This is very useful when you expect a reply from an Actor.
        // And until you get a response you register for its deathwatch because he might die in the meantime.
      // And when you get the response that you want you naturally unsubscribed from the actor's death watch

      case Terminated(ref) =>
        log.info(s"The reference that I'm watching $ref has been stopped")

    }
  }

  val watcher = system.actorOf(Props[Watcher], "watcher")
  watcher ! StartChild("watchedChild")

  val watchedChild = system.actorSelection("/user/watcher/watchedChild")
  Thread.sleep(500)

  watchedChild ! PoisonPill // [akka://StoppingActors/user/watcher] The reference that I'm watching Actor[akka://StoppingActors/user/watcher/watchedChild#2142551985] has been stopped

}
