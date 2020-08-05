package infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.routing.{ActorRefRoutee, Broadcast, FromConfig, RoundRobinGroup, RoundRobinPool, RoundRobinRoutingLogic, Router}
import com.typesafe.config.ConfigFactory

/**
  * Lecture 29 [Routers]
  *
  * - Routers are extremely useful when you want to delegate or spread work in between multiple actors of the same kind
  * - Routers are usually middle level actors that forward messages to other actors.
  * - Those are our routines either created by the routers themselves or from the outside.
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418716#notes
  */

object Routers extends App {

  /**
    #1 Manual Router
   */
  class Master extends Actor {

    // Step 1: Create Routees
    private val slaves = for(i <- 1 to 5) yield {
      val slave = context.actorOf(Props[Slave], s"slave_$i")
      context.watch(slave)

      ActorRefRoutee(slave)
    }

    // Step 2: Define Router
    // Supported Options for routing logic are as follows
    // - Round robin -> which we've seen that cycle between Routees
    // - Random -> which is not very smart.
    // - Smallest mailbox -> As a load balancing heuristics as it always sends the next message to the actor with the fewest messages in the queue.
    // - Broadcast -> As a redundancy measure which sends the same message to all the routines.
    // - scatter-gather-first -> It broadcasts i.e. sends to everyone and waits for the first reply and all the next replies are discarded.
    // - tail-chopping -> It forwards the next message to each actor sequentially until the first reply was received and all the other replies are discarded.
    // - consistent-hashing -> In which all the messages with the same hash get to the same actor
    private var router = Router(RoundRobinRoutingLogic(), slaves)


    override def receive: Receive = {

      // Step 4: Handle the termination / lifecycle of Routees
      case Terminated(ref) =>
        router = router.removeRoutee(ref) // As `removeRoutee` returns a new Router

        val newSlave = context.actorOf(Props[Slave])
        context.watch(newSlave)
        router = router.addRoutee(newSlave) // As `addRoutee` returns a new Router

      // Step 3: Route the messages
      case message =>
        router.route(message, sender()) // Slave actors reply directly to the sender without involving me
    }
  }


  class Slave extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("Routers", ConfigFactory.load().getConfig("routersDemo"))
  val master = system.actorOf(Props[Master])

//  for (i <- 1 to 10) {
//    master ! s"[$i] Hello from the world"
//  }

  /**
    # 2 Router Actor with its own Children
   */

  // 2.1 Programmatically [In Code]
  val poolMaster = system.actorOf(RoundRobinPool(5).props(Props[Slave]), "simplePoolMaster")

//  for (i <- 1 to 10) {
//    poolMaster ! s"[$i] Hello from the world"
//  }

  // 2.2 From Configuration
  val poolMaster2 = system.actorOf(FromConfig.props(Props[Slave]), "poolMaster2")
  // It will look into the configuration and it will search for the deployment or some configuration associated to `poolMaster2` actor and it will attach props slave.
  // When the actor system looks at the configuration for `poolMaster2` it will see that it has a router of type `round-robin-pool` and number of instances equals five.
  // And it only needs the kind of props to instantiate and we have supplying the props in `FromConfig.props(Props[Slave]`.

//  for (i <- 1 to 10) {
//    poolMaster2 ! s"[$i] Hello from the world"
//  }

  /**
      # 2 Router with Actors created elsewhere
      Group Router
    */
  // So let's say in another part of my application somebody created slave list as
  val slaveList = (1 to 5).map(i => system.actorOf(Props[Slave], s"slave_$i")).toList

  // I need their Paths
  val slavePaths = slaveList.map(slaveRef => slaveRef.path.toString)

  // 3.1 in the code
  val groupMaster = system.actorOf(RoundRobinGroup(slavePaths).props())

//    for (i <- 1 to 10) {
//      groupMaster ! s"[$i] Hello from the world"
//    }

  // 3.2 from Configuration
  val groupMaster2 = system.actorOf(FromConfig.props(), "groupMaster2")

//  for (i <- 1 to 10) {
//    groupMaster2 ! s"[$i] Hello from the world"
//  }

  /**
    * Handling of Special Messages
    */

  groupMaster2 ! Broadcast("Hello, everyone")

  // PoisonPill and Kill are not routed. They are handled by the routing actor.
  // Second there are management messages such as `addRoutee`, `Remove`, `Get` which are handled only by the routing actor.

}
