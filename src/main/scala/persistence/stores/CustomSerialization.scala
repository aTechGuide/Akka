package persistence.stores

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory

/**
  * Persistence Lecture 16 [Custom Serialization]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002466#overview
  */

// Command
case class RegisterUser(email: String, name: String)

// Event
case class UserRegistered(id: Int, email: String, name: String)

// Serializer
class UserRegistrationSerializer extends Serializer {

  val SEPARATOR = "//"

  // This is used to identify this Serializer as an Int in the Akka guts
  override def identifier: Int = 12345 // Any number will do

  // This will turn an object as an AnyRef into an array of bytes (Array[Byte]) which will then be written either to a file or two whatever the Journal is actually implemented with.
  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event @ UserRegistered(id, email, name) =>
      println(s"Serializing $event")
      s"[$id$SEPARATOR$email$SEPARATOR$name]".getBytes

    case _ => throw new IllegalArgumentException("Only UserRegistered event is supported")
  }

  // Takes an Array and returns Object that we want.
  // `manifest` is used to instantiate the proper class that we want via reflection.
  // This manifest will be passed with some class if the method `includeManifest` returns true.
  // So if `includeManifest` returns true the actual class here will be passed so that we can use this class's constructor to instantiate the proper object from these bytes that you pass.
  // But for our purposes we don't need to.
  // And if `includeManifest` method returns false this manifest here when this method is called will have `None` in the parameter
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val string = new String(bytes)
    val values = string.substring(1, string.length-1).split(SEPARATOR)

    val result = UserRegistered(values(0).toInt, values(1), values(2))
    println(s"Deserialized $result")

    result
  }

  override def includeManifest: Boolean = false

}


// Actor
class UserRegistrationActor extends PersistentActor with ActorLogging {

  var currentId = 0

  override def receiveRecover: Receive = {
    case event @ UserRegistered(id, _, _) =>
      log.info(s"Recovered $event")
      currentId = id
  }

  // When we pass send a command to the user registration actor
  // - It will call the persist method which will attempt to send `UserRegistered` to the Journal
  // - Before the `UserRegistered` event gets to the journal it passes through the Serializer which will actually convert this into the bytes that this journal will expect
  // - When we implement our custom serializer, we will configure it so that it will sit between our `UserRegistrationActor` and the journal
  //
  // FLOW
  // - We send a command to the actor
  //  - Actor calls persist
  //  - The serializer, serializes the event into bytes
  //  - The Journal writes the bytes.
  //
  //  And when the actor does the recovery the opposite happens
  //  - The Journal will send the events back to the actor
  //  - The serialized will call the `fromBinary` method to turn the array of bytes into the actual objects that the actor will then expect in `receiveRecover`.
  //
  //  The flow will go exactly backwards.
  override def receiveCommand: Receive = {
    case RegisterUser(email, name) =>
      persist(UserRegistered(currentId, email, name)) { e =>
        currentId += 1
        log.info(s"Persisted $e")
      }
  }

  override def persistenceId: String = "user-registration"
}

object CustomSerialization extends App {

  val system = ActorSystem("CustomSerialization", ConfigFactory.load("application-persistence.conf").getConfig("customSerializer"))
  val userRegistrationActor = system.actorOf(Props[UserRegistrationActor], "userRegistrationActor")

//  for (i <- 1 to 10) {
//    userRegistrationActor ! RegisterUser(s"user_$i@rtjvm.com", s"user $i")
//  }



}
