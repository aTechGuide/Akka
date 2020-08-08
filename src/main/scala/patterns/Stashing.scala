package patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

/**
  * Lecture 32 [Stashing$]
  *
  * - Stashes allow actors to set messages aside for later that they can't or they should not process at this exact moment in time.
  * - So when the actor changes behavior by calling context become or Unbecome it's usually a good time to prepare them to the mailbox and start processing them again.
  *
  * Remember
  * - First of all there are potential memory bounds on stash although this is rarely a problem in practice because Sashes are backed by vectors so you can safely store away millions of messages without any significant performance impact.
  *
  * - Depending on which mailbox you use Un stashing may pose problems to the bounds of your mailbox because if you store away millions of messages and there is no more room in your mailbox the mailbox will overflow and some messages might end up being dropped.
  *   Either way at the end of Un stashing the stash will be guaranteed to be empty.
  *
  * - Make sure you don't stash the same message twice because it will throw an exception.
  *
  * - And finally make sure you mix in your stash trait last due to trait linearization.
  *   - Because stash overrides preRestart the stash trait must be mixed in last. So that super can work well.
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418722#notes
  */

object Stashing extends App {

  /*
    ResourceActor
      - Open -> It can receive read/write requests to resource
      - otherwise it will postpone all read/write requests until the state is open

    ResourceActor starts with closed State
      - When it receices Open => Will switch to open state
      - Read/Write messages are POSTPONED


    ResourceActor is Open
      - Handles read/write messages
      - Receive the close message to switch to close state

    CASE [Open, Read, Read, Write]
    - Switch to Open
    - Read the data
    - Read the data
    - Write the data

    CASE [Read, Open, Write]
    - Stach Read
      - stash: [Read]
    - Switch to Open and Stashed message is prepended to mailbox
    - Read the data
    - Write the data

   */

  case object Open
  case object Close
  case object Read
  case class Write(data: String)

  // Step 1: Mixin Stash Trait
  class ResourceActor extends Actor with ActorLogging with Stash {

    private var innerData: String = ""

    override def receive: Receive = closed

    def closed: Receive = {
      case Open =>
        log.info("Opening Resource")
        // Step 3: InstashAll when we switch the message handler
        unstashAll() //<- Before we switch the context to open we will empty the stash and prepend all the messages to my normal mailbox in the hope that the next message handler will be able to process those messages.
        context.become(open)
      case message =>
        log.info("Stashing Message as I can NOT handle it in closed State")

        // Stash messages we can NOT handle
        stash()
    }

    def open: Receive = {
      case Read =>
        log.info(s"I've read $innerData")
      case Write(data) =>
        log.info(s"I'm writting $data")
        innerData = data
      case Close =>
        log.info("Closing Resource")
        unstashAll()
        context.become(closed)
      case message =>
        log.info(s"Stashing the $message as I can NOT handle it in Open State")
        stash()

    }
  }

  val system = ActorSystem("StashDemo")

  val resourceActor = system.actorOf(Props[ResourceActor], "resourceActor")

  resourceActor ! Read // Stashed
  resourceActor ! Open // Handled and pops messages from Stash
  resourceActor ! Open // Stashed
  resourceActor ! Write("Message") // Handled
  resourceActor ! Close // Handled + Pops Open message + again switched to Open
  resourceActor ! Read // Handled

}
