package basics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

/**
  * Lecture 16 [Child Actors Exercise]
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418652#overview
  */

object ChildActorsExercise extends App {

  // Distributed Word Counting
  object WordCounterMaster {
    case class Initialize(nChildren: Int)
    case class WordCountTask(id: Int, text: String)
    case class WordCountReply(id: Int, count: Int)

  }
  class WordCounterMaster extends Actor {

    import WordCounterMaster._

    override def receive: Receive = {
      case Initialize(nChildren: Int) =>
        val childrenRefs = for ( i <- 1 to nChildren) yield context.actorOf(Props[WordCounterWorker], s"wcw_$i")
        context.become(withChildren(childrenRefs, 0, 0, Map()))
    }

    def withChildren(childrenRefs: Seq[ActorRef], currentChildIndex: Int, currentTaskID: Int, requestMap: Map[Int, ActorRef]): Receive = {
      case text: String =>

        val task = WordCountTask(currentTaskID, text)
        val childRef = childrenRefs(currentChildIndex)
        childRef ! task

        val nextChildIndex = (currentChildIndex + 1) % childrenRefs.length
        val originalSender = sender()
        val newRequestMap = requestMap + (currentTaskID -> originalSender)
        context.become(withChildren(childrenRefs, nextChildIndex, currentTaskID + 1, newRequestMap))

      case WordCountReply(id, count) =>
        val originalSender = requestMap(id)
        originalSender ! count
        context.become(withChildren(childrenRefs, currentChildIndex, currentTaskID, requestMap - id))
    }
  }

  class WordCounterWorker extends Actor {

    import WordCounterMaster._

    override def receive: Receive = {
      case WordCountTask(id, text) => sender() ! WordCountReply(id, text.split(" ").length)
    }
  }

  /*
    Flow
    - Create WordCounterMaster and initialize it with 10 Workers
    - Send Text to WordCounterMaster, WordCounterMaster will send WordCountTask to one of its children.
    - Child will reply with WordCountReply to the master
    - Master replies to Sender

    We use Round Robin to load balance

   */

  class TestActor extends Actor {

    import WordCounterMaster._
    override def receive: Receive = {
      case "go" =>
        val master = context.actorOf(Props[WordCounterMaster], "master")
        master ! Initialize(3)

        val texts = List("I Love Akka", "Scala is super dope", "yes", "me too")
        texts.foreach {text => master ! text}

      case count: Int => println(s"[Test Actor] I received Reply $count")
    }
  }

  val system = ActorSystem("RoundRobinWC")
  val testActor = system.actorOf(Props[TestActor], "testActor")

  testActor ! "go"
  /*
    [Test Actor] I received Reply 4
    [Test Actor] I received Reply 1
    [Test Actor] I received Reply 3
    [Test Actor] I received Reply 2
   */


}
