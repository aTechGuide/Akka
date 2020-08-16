package infra

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

/**
  * Lecture 31 [Mailboxes]
  *
  * - Mailboxes are those data structures in the actor reference but store messages.
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418720#notes
  */

object Mailboxes extends App {

  val system = ActorSystem("MailboxeDemo", ConfigFactory.load().getConfig("mailboxesDemo") )

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }

  }

  /**
    *
    * Case #1: Custom Priority Mailbox
    *
    * - Normally mail boxes just enqueue every single message that gets sent to an actor in a regular queue.
    *   And we would like to add some priority to this message.
    *
    * - We'd like to prioritise messages that start with 0 and then P1 and then P2 and P3 in this order.So we'd like to prioritise messages that start with 0 and then P1 and then P2 and P3 in this order.
    */

  // Step 1: Mailbox Definition
  class SupportTicketPriorityMailbox(settings: ActorSystem.Settings, config: Config)
    extends UnboundedPriorityMailbox(PriorityGenerator {
      case message: String if message.startsWith("[P0]") => 0 // Lower means higher priority
      case message: String if message.startsWith("[P1]") => 1 // Lower means higher priority
      case message: String if message.startsWith("[P2]") => 2 // Lower means higher priority
      case message: String if message.startsWith("[P3]") => 3 // Lower means higher priority
      case _ => 4
    })

  // Step 2: Make it known in Configuration
    // Changes in config files

  // Step 3: Attach the dispatcher to an actor

  val supportTicketLogger = system.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"))

  supportTicketLogger ! PoisonPill // Even this message is postponed
  supportTicketLogger ! "[P3] This is nice to have"
  supportTicketLogger ! "[P0] Solve Now"
  supportTicketLogger ! "[P1] Do this"

  // Question is
  // After which time can I send another message and be prioritized accordingly.

  // Answer
  // The sad answer is that neither can you know.
  // Nor can you configure the wait because when a threat is allocated to dequeue messages from this actor whatever is put on the queue in that particular order which is ordered by the mailbox will get handled.

  /**
    * Case #2: Control Aware Mailbox
    *
    * - The problem that we want to solve is that some messages need to be processed first regardless of what's been cued up in the mailbox.
    *   For example a management ticket for our ticketing system that we saw above
    *
    * -  And for this one we don't need to implement a custom mailbox.
    */

  // Step 1: Mark important messages as control messages
  case object ManagementTicket extends ControlMessage

  // Step 2 [Method #1]: Configure who gets the mailbox
  // - Make the actor attach to the mailbox

  val controlAwareActor = system.actorOf(Props[SimpleActor].withMailbox("control-aware"))

  controlAwareActor ! "[P0] Solve Now"
  controlAwareActor ! "[P1] Do this"
  controlAwareActor ! ManagementTicket

  // Step 2 [Method #2] Using the deployment Config
  val alternativeControlAwareActor = system.actorOf(Props[SimpleActor], "alternativeControlAwareActor")

  alternativeControlAwareActor ! "[P0] Solve Now"
  alternativeControlAwareActor ! "[P1] Do this"
  alternativeControlAwareActor ! ManagementTicket








}
