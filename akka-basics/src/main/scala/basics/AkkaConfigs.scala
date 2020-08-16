package basics

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

/**
  * Lecture 18 [Akka Configuration]
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418656#overview
  */

object AkkaConfigs extends App {

  class SimpleLoggingActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  // Method 1: Inline Configuration
  val configString =
    """
      | akka {
      |   loglevel = "DEBUG"
      | }
    """.stripMargin

  val config = ConfigFactory.parseString(configString)
  val system = ActorSystem("ConfigDemo", ConfigFactory.load(config))
  val actor = system.actorOf(Props[SimpleLoggingActor])

  actor ! "A message to Remember"

  // Method 2: Config File
  val system2 = ActorSystem("DefaultConfigFile")
  val defaultConfigActor = system2.actorOf(Props[SimpleLoggingActor])
  defaultConfigActor ! "Remember Me !"

  // Method 3: Separate Config in Same File
  val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
  val system3 = ActorSystem("SpecialConfigFile", specialConfig)
  val specialConfigActor = system3.actorOf(Props[SimpleLoggingActor])
  specialConfigActor ! "Remember Me ! I'm Special"

  // Method 4: Separate Config in Another File
  val separateConfig = ConfigFactory.load("secretFolder/secretConfiguration.conf")
  println(separateConfig.getString("akka.loglevel"))

  /**
    * File Formats
    * - .conf files
    * - .properties files
    */

  val jsonConfig = ConfigFactory.load("json/jsonConfig.json")
  println(jsonConfig.getString("aJson"))
  println(jsonConfig.getString("akka.loglevel"))

  val propsConfig = ConfigFactory.load("props/propsConfiguration.properties")
  println(propsConfig.getString("my.simpleProperty"))
  println(propsConfig.getString("akka.loglevel"))


}
