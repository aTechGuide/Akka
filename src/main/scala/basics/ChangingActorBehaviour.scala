package basics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import basics.ChangingActorBehaviour.Mom.MomStart

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
        if (state == HAPPY) sender() ! KidAccept
        else sender() ! KidReject
    }
  }

  object Mom {
    case class MomStart(kidRef: ActorRef) // Message sent from outside to kickstart
    case class Food(food: String)
    case class Ask(message: String) // e.g. Do you want to play?
    val VEGETABLES = "veggies"
    val CHOCOLATE = "veggchocolateies"
  }

  class Mom extends Actor {
    import Mom._
    import FussyKid._

    override def receive: Receive = {
      case MomStart(kidRef) =>
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

  mom ! MomStart(fuzzyKid)

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

    override def receive: Receive = happyReceive //<- This is calling the method which returns the object. That object will be invoked by Akka on every arriving messages

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
  mom ! MomStart(statelessFussyKid)

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
      case Food(CHOCOLATE) => context.unbecome()
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


}
