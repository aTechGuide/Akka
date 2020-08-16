package persistence.stores

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

/**
  * Persistence Lecture 13 [Local Stores]
  *
  * Level DB
  * - It is a file based key-value store [Written by google]
  * - Supports Compaction
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002460#overview
  */

object LocalStores extends App {

  val system = ActorSystem("LocalStores", ConfigFactory.load("application-persistence.conf").getConfig("localStores"))
  val persistentActor = system.actorOf(Props[SimplePersistentActor], "persistentActor")

  for (i <- 1 to 10) {
    persistentActor ! s"I love Akka $i"
  }

  persistentActor ! "print"
  persistentActor ! "snap"

  for (i <- 11 to 20) {
    persistentActor ! s"I love Akka $i"
  }

}
