package persistence.practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

/**
  * Persistence Lecture 17 [Schema Evolution and Event Adapters]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002476
  */


object EventAdapters extends App {

  /*
    Store for Guitars
   */

  // Data Structures
  val ACOUSTIC = "acoustic"
  val ELECTRIC = "electric"

  case class Guitar(id: String, model: String, make: String, guitarType: String = ACOUSTIC)

  // Command
  case class AddGuitar(guitar: Guitar, quantity: Int)

  // Event
  case class GuitarAdded(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int)
  case class GuitarAddedV2(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int, guitarType: String)

  class InventoryManager extends PersistentActor with ActorLogging {

    val inventory: mutable.Map[Guitar, Int] = new mutable.HashMap[Guitar, Int]()

    override def receiveRecover: Receive = {

      case event @ GuitarAddedV2(id, model, make, quantity, guitarType) =>

        log.info(s"Recovered $event")

        val guitar = Guitar(id, model, make, guitarType)
        addGuitarInventory(guitar, quantity)

    }

    override def receiveCommand: Receive = {
      case AddGuitar(guitar @ Guitar(id, model, make, guitarType), quantity) =>
        persist(GuitarAddedV2(id, model, make, quantity, guitarType)) { _ =>

          addGuitarInventory(guitar, quantity)

          log.info(s"Added $quantity x $guitar to inventory ")
        }

      case "print" =>
        log.info(s"Current inventory is: $inventory")
    }

    override def persistenceId: String = "guitar-inventory-manager"

    def addGuitarInventory(guitar: Guitar, quantity: Int) = {
      val existingQuantity = inventory.getOrElse(guitar, 0)
      inventory.put(guitar, existingQuantity + quantity)
    }
  }

  // ReadEventAdapter was built specifically for schema evolution problems that we're dealing in this lecture.
  class GuitarReadEventAdapter extends ReadEventAdapter {
    /*
      Journal ->  Serializer ->   Read Event Adapter -> Actor
      (bytes)     (GuitarAdded)   (GuitarAddedV2)       (receiveRecover)

      Pipeline
      - The journal sends bytes
      - The serializer turns that into some kind of object
      - The ReadEventAdapter turns that into some other kind of object that the actor can then handle in the receiveRecover.
     */

    // `fromJournal` method
    // - Takes an event in the form of an object which is the output of the Deserialization step
    // - And the manifest as a string which is a type hint that we can use to instantiate some other type
    // - Output of this method is an event sequence that is a sequence of multiple events that the actor will handle in turn.
    //    - If we somehow decide to split the event into multiple events this event sequence type will handle that case.
    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case GuitarAdded(guitarId, guitarModel, guitarMake, quantity) =>
        EventSeq.single(GuitarAddedV2(guitarId, guitarModel, guitarMake, quantity, ACOUSTIC))

      case other => EventSeq.single(other)
    }

    // We also Have a WriteEventAdapter [Used for backward Compatibility]
    // actor -> WriteEventAdapter -> Serializer -> Journal

    // if we decide to write the same logic in the ReadEventAdapter and WriteEventAdapter instead of duplicating the code
    // we'll just simply extend EventAdapter which is simply a trait that mixes in both ReadEventAdapter and WriteEventAdapter
  }

  val system = ActorSystem("CustomSerialization", ConfigFactory.load("application-persistence.conf").getConfig("eventAdaptors"))
  val inventoryManager = system.actorOf(Props[InventoryManager], "inventoryManager")

  val guitars = for (i <- 1 to 10) yield Guitar(s"$i", s"Hakker $i", "rtJVM")

//  guitars.foreach { guitar =>
//    inventoryManager ! AddGuitar(guitar, 5)
//  }

  inventoryManager ! "print"

}
