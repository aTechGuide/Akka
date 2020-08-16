package persistence.stores

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

/**
  * Persistence Lecture 14 [Postgres SQL]
  *
  * SQL inside container
  * - select * from public.journal;
  * - select * from public.snapshot;
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002462
  */

object Postgres extends App {

  val system = ActorSystem("PostgresSystem", ConfigFactory.load("application-persistence.conf").getConfig("postgres"))
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
