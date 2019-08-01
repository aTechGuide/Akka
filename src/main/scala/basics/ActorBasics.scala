package basics

import akka.actor.{Actor, ActorSystem, Props}

object ActorBasics extends App {

  /*
    - Actors are similar to objects as both have data.
    - Actors store state as data
    - We send messages to communicate with an Actor

    In Nutshell,
    "Actors are objects we can't access directly, but only send messages to"
   */

  /*
    ActorSystem
    - Datastructure which controls Number of threads under the hood. These threads are allocated to running actors
    - Have one ActorSystem per instance (unless we have reason to create more)
   */

  // Part 1: Create Actor System
  val actorSystem = ActorSystem("firstActorSystem")
  println(actorSystem.name)

  // Part2: Create Actors

  /*
    Actors
    - Actors are uniquely identified by "name"
    - They are kind of humans talking to each other via messages
    - Messages are passed and processed asynchronously (actors reply when they can)
    - Actor may respond differently
    - Actors are really encapsulated (we can invade their mind / Force them to provide the info we need)
   */

  // word count actor
  class WordCountActor extends Actor {

    // internal data
    var totalWords = 0

    // behaviour/Receive handler that Akka invokes
    def receive: PartialFunction[Any, Unit] = {
      case message: String =>
        println(s"Received Message: $message")
        totalWords += message.split(" ").length
      case msg => println(s"[WordCount] I cannot understand ${msg.toString}")
    }
  }

  // Part 3 Instantiating the actor
    // wordCounter is ActorRef (hence we can NOT poke data of an Actor neither can call their method)
    // We can NOT instantiate actor class by "new" keyword (i.e. by hand)
  val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter")
  val anotherWordCounter = actorSystem.actorOf(Props[WordCountActor], "AnotherWordCounter")

  // new WordCountActor //<- "Compilation Error"

  // Part 4 Communicate
  wordCounter ! "I am learning Akka and it's pretty Cool" //<- Infix Notation to send Message "Asynchronously"; "!" is also known as "tell"
  anotherWordCounter ! "A different message"

  /*
    How to instantiate Actor with a constructor argument
   */
  class Person(name: String) extends Actor {
    override def receive: Receive = {
      case "hi" => println(s"Hi, my name is $name")
      case _ =>
    }
  }

  val person = actorSystem.actorOf(Props(new Person("Bob"))) //<- Inside prop `new Person("Bob")` is legal BUT DISCOURAGED
  person ! "hi"

  // Better way to instantiate Actor with a constructor
    // Declare Companion object
  object Person {
    def props(name: String) = Props(new Person(name))
  }

  val betterPersonInstantiation = actorSystem.actorOf(Person.props("Better Bob"))
  betterPersonInstantiation ! "hi"



}
