package patterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, FSM, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, OneInstancePerTest, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


/**
  * Lecture 34 [Finite State Machines]
  *
  * Finite State Machines are an alternative to `context.become`
  * because often times we have actors with a very complex piece of logic and `context.become` might be extremely hard to read
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418730
  */

class FiniteStateMachinesSpec extends TestKit(ActorSystem("FSMSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll with OneInstancePerTest {

  import FiniteStateMachinesSpec._

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def runTestSuite(props: Props): Unit = {

    "error when Not initialized" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! RequestProduct("coke")
      expectMsg(VendingError("MachineNotInitialized"))
    }

    "report a product not available " in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 1))
      vendingMachine ! RequestProduct("sandwitch")
      expectMsg(VendingError("ProductNotAvailable"))
    }

    "throw timeout If I do NOT insert Money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 1))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please Insert 1 dollars"))

      within(1.5 seconds) {
        expectMsg(VendingError("RequestTimedOut"))
      }
    }

    "handle the reception of partial money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please Insert 3 dollars"))

      vendingMachine ! ReceiveMoney(1)
      expectMsg(Instruction("Please Insert 2 dollars"))

      within(1.5 seconds) {
        expectMsg(VendingError("RequestTimedOut"))
        expectMsg(GiveBackChange(1))
      }
    }

    "deliver the product uf I insert all the money" in {

      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please Insert 3 dollars"))

      vendingMachine ! ReceiveMoney(3)
      expectMsg(Deliver("coke"))

    }

    "give back change and be able to request money for a new product" in {

      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please Insert 3 dollars"))

      vendingMachine ! ReceiveMoney(4)
      expectMsg(Deliver("coke"))
      expectMsg(GiveBackChange(1))

      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please Insert 3 dollars"))

    }

  }

  " A Vending Machine" should {
    runTestSuite(Props[VendingMachine])

  }

  " A FSM Vending Machine" should {
    runTestSuite(Props[VendingMachineFSM])
  }

}

object FiniteStateMachinesSpec {

  /*
    Vending Machine
   */

  case class Initialize(inventory: Map[String, Int], prices: Map[String, Int])
  case class RequestProduct(product: String)
  case class Instruction(instruction: String) // message the vending machine will show on screen
  case class ReceiveMoney(amount: Int)
  case class Deliver(product: String)
  case class GiveBackChange(amount: Int)

  case class VendingError(reason: String)
  case object ReceiveMoneyTimeout // if we request a product the machine instructs us to insert for example 3 dollars and we don't do that in for example 5 seconds.
  // The machine will send itself or receive money timeout and it will revert back to its original state.


  /*
    Logic
    - It will receive this initialize message at first and any user will have to send it a request product message.
    - The vending machine will reply with an instruction message and the user has to send it the received money message.
    - And as a response the vending machine will deliver the product and give back an amount of change if need be
    - In case of an error, the vending machine will send back a vending error.
    - If the user doesn't send the money within a certain time frame the vending machine will send itself or receive money time out and then it will revert back to its original state.

   */

  class VendingMachine extends Actor with ActorLogging {
    implicit val executionContext: ExecutionContext = context.dispatcher
    override def receive: Receive = idle

    def idle: Receive = {
      case Initialize(inventory, prices) => context.become(operational(inventory, prices))
      case _ => sender() ! VendingError("MachineNotInitialized")
    }

    def operational(inventory: Map[String, Int], prices: Map[String, Int]): Receive = {
      case RequestProduct(product) => inventory.get(product) match {
        case None | Some(0) => sender() ! VendingError("ProductNotAvailable")

        case Some(_) =>
          val price = prices(product)
          sender() ! Instruction(s"Please Insert $price dollars")
          context.become(waitForMoney(inventory, prices, product, 0, startReceiveMoneyTimeoutSchedule, sender()))
      }
    }

    def startReceiveMoneyTimeoutSchedule: Cancellable = context.system.scheduler.scheduleOnce(1 second) {
      self ! ReceiveMoneyTimeout
    }

    def waitForMoney(inventory: Map[String, Int], prices: Map[String, Int], product: String, money: Int, moneyTimeoutSchedule: Cancellable, requestor: ActorRef): Receive = {
      case ReceiveMoneyTimeout =>
        requestor ! VendingError("RequestTimedOut")
        if (money > 0) requestor ! GiveBackChange(money)
        context.become(operational(inventory, prices))

      case ReceiveMoney(amount) =>
        moneyTimeoutSchedule.cancel()
        val price = prices(product)
        if (money + amount >= price) {

          // User Buys Product
          requestor ! Deliver(product)

          // Deliver Change
          val change = money + amount - price
          if (change > 0) requestor ! GiveBackChange(change)

          // Update Inventory
          val newStock = inventory(product) - 1
          val newInventory = inventory + (product -> newStock)

          context.become(operational(newInventory, prices))
        } else {
          val remainingMoney = price - money - amount
          requestor ! Instruction(s"Please Insert $remainingMoney dollars")
          context.become(waitForMoney(inventory, prices, product, money + amount, startReceiveMoneyTimeoutSchedule, requestor))
        }
    }
  }

  /**
    * FSM
    */

  // Step 1 - Define the states and Data of the actor
  trait VendingState
  case object Idle extends VendingState
  case object Operational extends VendingState
  case object WaitForMoney extends VendingState

  trait VendingData
  case object Uninitialized extends VendingData
  case class Initialized(inventory: Map[String, Int], prices: Map[String, Int]) extends VendingData
  case class WaitForMoneyData(inventory: Map[String, Int], prices: Map[String, Int], product: String, money: Int, requestor: ActorRef) extends VendingData

  class VendingMachineFSM extends FSM[VendingState, VendingData] {
    // We don't have receive handler

    // FSM triggers an Event on receiving message for e.g. EVENT(message, data) where `data` = Data currently on Hold of this FSM
    // Our job as a programmer of an FSM is to handle States and events not messages.

    /*
      An FSM is basically an actor which at any point has a state and some data
      A state is an instance of a class i.e. an object and the data is also an instance of a class that's an object.

      In a reaction to an event then both the states and the data might be replaced with different values. So state and data can be changed.

      We're starting initially with Idle.
      - So states = Idle and data = uninitialized

      As a reaction to an `Initialize(inventory, prices)` message this will trigger an `Event(Initialize(inventory, prices), Uninitialized)`
      - So state = operational and data = Initialized(inventory, prices)

      Event(RequestProduct(coke), Initialized(inventory, prices))
      - So state = WaitForMoney and data = WaitForMoneyData(...)

      Event(ReceiveMoney(amount), WaitForMoneyData(inventory, prices, product, money, requestor))
      - So state = Operational and data = Initialized(newInventory, prices)
     */

    startWith(Idle, Uninitialized)

    when(Idle) {
      case Event(Initialize(inventory, prices), Uninitialized) =>
        goto(Operational) using Initialized(inventory, prices)
        // equivalent to context.become(operational(inventory, prices))
      case _ =>
        sender() ! VendingError("MachineNotInitialized")
        stay()
    }

    when(Operational) {
      case Event(RequestProduct(product), Initialized(inventory, prices)) =>
        inventory.get(product) match {
          case None | Some(0) =>
            sender() ! VendingError("ProductNotAvailable")
            stay()

          case Some(_) =>
            val price = prices(product)
            sender() ! Instruction(s"Please Insert $price dollars")

            goto(WaitForMoney) using WaitForMoneyData(inventory, prices, product, 0, sender())
        }
    }

    when(WaitForMoney, stateTimeout = 1 second) {
      case Event(StateTimeout, WaitForMoneyData(inventory, prices, product, money, requester)) =>

        requester ! VendingError("RequestTimedOut")
        if (money > 0) requester ! GiveBackChange(money)

        goto(Operational) using Initialized(inventory, prices)

      case Event(ReceiveMoney(amount), WaitForMoneyData(inventory, prices, product, money, requester)) =>

        val price = prices(product)
        if (money + amount >= price) {

          // User Buys Product
          requester ! Deliver(product)

          // Deliver Change
          val change = money + amount - price
          if (change > 0) requester ! GiveBackChange(change)

          // Update Inventory
          val newStock = inventory(product) - 1
          val newInventory = inventory + (product -> newStock)

          goto(Operational) using Initialized(newInventory, prices)

        } else {
          val remainingMoney = price - money - amount
          requester ! Instruction(s"Please Insert $remainingMoney dollars")

          stay() using WaitForMoneyData(inventory, prices, product, money + amount, requester)
        }
    }

    // We can handle events that are not being handled anywhere else by using following method
    whenUnhandled {
      case Event(_, _) =>
        sender() ! VendingError("CommandNotFound")
        stay()
    }

    onTransition {
      case stateA -> stateB => log.info(s"Transitioning from $stateA to $stateB")
    }

    // This actor has not started yet until we called the `initialize()` method.
    initialize()

  }

}
