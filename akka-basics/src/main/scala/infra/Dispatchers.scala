package infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

/**
  * Lecture 30 [Dispatchers]
  *
  * Dispatchers are in charge of delivering and handling messages within an actor system.
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418718
  */

object Dispatchers extends App {

  class Counter extends Actor with ActorLogging {

    var count = 0

    override def receive: Receive = {
      case message =>
        count += 1
        log.info(s"[count = $count] message = $message")
    }
  }

  val system = ActorSystem("Dispatchers", ConfigFactory.load().getConfig("dispatchersDemo"))

  // Method - 1 -> Programmatically / In Code
  val actors = for( i <- 1 to 10) yield system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter_$i")

  val r = new Random()

  for (i <- 1 to 1000) {
    // actors(r.nextInt(10)) ! i
    // Actors are not scheduled until the dispatcher decides to allocate a thread for them. As `fixed-pool-size = 3` we have at max 3 Actors scheduled at any time
  }

  // Method - 2 -> From Config
  val rtjvmActor = system.actorOf(Props[Counter], "rtjvm") // the configuration will take care to attach my dispatcher `rtjvm` to this actor.

  /**
    * Dispatchers implement the ExecutionContext Trait
    */

  class DBActor extends Actor with ActorLogging {

    // implicit val executionContext: ExecutionContext = context.dispatcher

    // Solution #1: Using Dedicated Dispatcher
    implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("my-dispatcher")

    // Solution #2: Use Router [Refer Router Lecture]

    override def receive: Receive = {
      case message => Future {
        // hard computation
        Thread.sleep(5000)
        log.info(s"Success: $message")

      } //<- This future will run on the `context.dispatcher`
    }
  }

  // The running of future inside of actors is generally discouraged.
  // But in this particular case there is a problem with blocking calls inside future because
  // if you're running a future with a long or a blocking call you may starve the `context.dispatcher` of running threads which are otherwise used for handling messages
  // The dispatcher is limited and with increased load might be occupied serving messages to actors instead of running your future or the other way around.
  // You may starve the delivery of messages to actors.

  // if you do want to do it use a dedicated dispatcher for blocking calls

  val dbActor = system.actorOf(Props[DBActor], "DBActor")
  //dbActor ! 42


  val nonBlockingActor = system.actorOf(Props[Counter])
  for (i <- 1 to 1000) {
    val message = s"message_$i"
    dbActor ! message
    nonBlockingActor ! message
  }


}
