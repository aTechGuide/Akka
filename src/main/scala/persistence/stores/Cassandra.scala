package persistence.stores

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

/**
  * Persistence Lecture 15 [Cassandra SQL]
  *
  * An application that writes to Cassandra,
  * - Cassandra would automatically create the key space and the tables for us.
  *
  * CQL
  * - select * from akka.messages;
  * - select * from akka_snapshot.snapshots;
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002464
  * - https://doc.akka.io/docs/akka-persistence-cassandra/
  */

object Cassandra extends App {

  val system = ActorSystem("CassandraSystem", ConfigFactory.load("application-persistence.conf").getConfig("cassandra"))
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
