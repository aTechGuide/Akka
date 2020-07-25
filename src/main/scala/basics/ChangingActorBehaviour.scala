package basics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import basics.ChangingActorBehaviour.Mom.MomStart

/**
  *
  * Lecture 12, 13, 14 [Changing Actor Behaviour]
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418632#overview
  */
object ChangingActorBehaviour extends App {

  /**
   * Interaction 1
   */
  object FussyKid {
    case object KidAccept
    case object KidReject

    val HAPPY = "happy"
    val SAD = "sad"
  }

  class FussyKid extends Actor {
    import FussyKid._
    import Mom._

    // internal state of kid
    var state: String = HAPPY

    override def receive: Receive = {
      case Food(VEGETABLES) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) =>
        if (state == HAPPY) sender() ! KidAccept // Replying to Sender via sender()
        else sender() ! KidReject
    }
  }

  object Mom {

    case class Food(food: String)
    case class Ask(message: String) // e.g. Do you want to play?

    case class MomStart(kidRef: ActorRef) // Message sent from outside to kickstart

    val VEGETABLES = "veggies"
    val CHOCOLATE = "veggchocolateies"

  }

  class Mom extends Actor {
    import Mom._
    import FussyKid._

    override def receive: Receive = {

      case MomStart(kidRef) => // Trigger Message; Sent from outside
        // Test interaction
        kidRef ! Food(VEGETABLES)  // 1
        kidRef ! Ask("Do you want to play?") //2 {Message 1 and 2 will receive in exact order}
      case KidAccept => println("Yay, my kid is happy!")
      case KidReject => println("My kid is sad, but he's healthy")
    }
  }

  val system = ActorSystem("ChangingActorBEhaviourDemo")
  val fuzzyKid = system.actorOf(Props[FussyKid])
  val mom = system.actorOf(Props[Mom])

  mom ! MomStart(fuzzyKid) // Prints -> My kid is sad, but he's healthy

  /*
    Short comings of interaction 1
    - We need to offer different behaviour based on an internal state of Actor e.g based on child state he returns happy/Sad
    - We should not use `var` as actor state (e.g. child State)
   */

  /**
   * Interaction 2: Stateless fuzzy kid
   */

  class StatelessFussyKid extends Actor {

    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive //<- This is calling the method which returns `Receive` object. That object will be invoked by Akka on every arriving messages

    def happyReceive: Receive = {
      case Food(VEGETABLES) => context.become(sadReceive) // change my receive handler to sad Receive. `context.become(sadReceive)` Forces Akka to swap the handler with new Handler
      case Food(CHOCOLATE) => // Do nothing
      case Ask(_) => sender() ! KidAccept
    }

    def sadReceive: Receive = {
      case Food(VEGETABLES) => // stay SAD
      case Food(CHOCOLATE) => context.become(happyReceive) //change receive handler to happy receive
      case Ask(_) => sender() ! KidReject
    }
  }

  val statelessFussyKid = system.actorOf(Props[StatelessFussyKid])
  mom ! MomStart(statelessFussyKid) // Prints -> My kid is sad, but he's healthy

  /*
    mom receives MomStart
      kid receives Food(Veg) -> Kid will change handler to SadReceive
      kid receive Ask(play) -> kid replies with sad receive handler
    mom receives KidReject
   */

  /**
   * become() Method
   * - Changes the Actor's behavior to become the new 'Receive'
   * - It also accepts a boolean value (discardOld) as second parameter
   *   - if `discardOld = true` it will replace the top element (i.e. the current message handler) with new element (message handler)
   *   - if `discardOld = false` it will keep the current message handler and push the new message handler on top
   */

  /*
    If we pass `true` in become() method of `StatelessFussyKid` and send following messages
      - Food(veg)
        - message handler turns to sad receive
      - Food(chocolate)
        - message handler turns to happy receive

     If we pass `false` in become() method of `StatelessFussyKid` and send following messages
      - Food(veg)
        - STACK: `sad receive` <- `happy receive` (As we started with happy receive)
      - Food(chocolate)
        - STACK: `happy receive` <- `sad receive` <- `happy receive` (As we started with happy receive)

        In case of stacked message handlers, When Actor needs to handle a message Akka will call the top most message handler on stack.
        If stack is empty, Akka will call the plain receive method
   */

  // To pop the message handle we use unbecome()

  class StatelessFussyKidUnbecomeExample extends Actor {

    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(VEGETABLES) => context.become(sadReceive, discardOld = false)
      case Food(CHOCOLATE) => // Do nothing
      case Ask(_) => sender() ! KidAccept
    }

    def sadReceive: Receive = {
      case Food(VEGETABLES) => context.become(sadReceive, discardOld = false)
      case Food(CHOCOLATE) => context.unbecome() // `unbecome()` does NOT take any parameter
      case Ask(_) => sender() ! KidReject
    }
  }

  /*
    StatelessFussyKidUnbecomeExample behaviour

    - Food(veg)
      - STACK: `sad receive` <- `happy receive` (As we started with happy receive)
    - Food(veg)
      - STACK: `sad receive` <- `sad receive` <- `happy receive`
    - Food(chocolate)
      - STACK: `sad receive` <- `happy receive` [One `sad receive` is popped out]
    - Food(chocolate)
      - STACK: `happy receive` [One `sad receive` is popped out]
   */

  /**
    * Exercises
    *
    */

  // Counter Actor with context.become and NO MUTABLE STATE

  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }

  class Counter() extends Actor {

    import Counter._

    override def receive: Receive = countReceive(0)

    def countReceive(currentCount: Int): Receive = {
      case Increment => context.become(countReceive(currentCount + 1))
      case Decrement => context.become(countReceive(currentCount -1))
      case Print => println(s"[Counter] my current count is $currentCount")
    }
  }

  import Counter._
  val counterActor = system.actorOf(Props[Counter], "Counter")

  (1 to 5).foreach(_ => counterActor ! Increment)
  (1 to 3).foreach(_ => counterActor ! Decrement)

  counterActor ! Print // [Counter] my current count is 2

  // Simplified Voting System

  case class Vote(candidate: String)
  class Citizen extends Actor {

    override def receive: Receive = voted(None)

    def voted(candidate: Option[String]): Receive = {
      case Vote(c) => context.become(voted(Some(c)))
      case VoteStatusRequest => sender() ! VoteStatusReply(candidate)
    }
  }

  case class AggregateVotes(citizens: Set[ActorRef])
  case object VoteStatusRequest
  case class VoteStatusReply(candidate: Option[String])
  class VoteAggregator extends Actor {
    override def receive: Receive = awaitingCommand

    def awaitingCommand: Receive = {

      case AggregateVotes(citizens) =>
        citizens.foreach { citizen => citizen ! VoteStatusRequest }
        context.become(awaitingStatuses(citizens, Map()))
    }

    def awaitingStatuses(stillWaiting: Set[ActorRef], currentStatuses: Map[String, Int]): Receive = {

      case VoteStatusReply(None) => sender() ! VoteStatusRequest // Might end up in infinite Loop
      case VoteStatusReply(Some(candidate)) =>
        val newStillWaiting = stillWaiting - sender()
        val currVote = currentStatuses.getOrElse(candidate, 0)
        val newStatuses = currentStatuses + (candidate -> (currVote + 1))

        if (newStillWaiting.isEmpty) println(s"[Aggregator] poll status: $newStatuses")
        else context.become(awaitingStatuses(newStillWaiting, newStatuses))

    }
  }

  val alice = system.actorOf(Props[Citizen])
  val bob = system.actorOf(Props[Citizen])
  val charlie = system.actorOf(Props[Citizen])
  val daniel = system.actorOf(Props[Citizen])

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  daniel ! Vote("Roland")

  val voteAggregator = system.actorOf(Props[VoteAggregator])
  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel)) // [Aggregator] poll status: Map(Jonas -> 1, Roland -> 2, Martin -> 1)

}
