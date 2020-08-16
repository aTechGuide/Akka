package faultTolerance

import java.io.File

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration._
import scala.io.Source

/**
  * Lecture 27 [Backoff Supervisor Pattern]
  * - In Notes [Actor Supervision]
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418688
  */

object BackoffSupervisorPattern extends App {

  case object ReadFile
  class FileBasedPersistentActor extends Actor with ActorLogging {

    var dataSource: Source = null

    override def preStart(): Unit = log.info("Persistent Actor Starting")

    override def postStop(): Unit = log.warning("Persistent Actor has Stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = log.warning("Persistent Actor Re Starting")

    override def receive: Receive = {
      case ReadFile =>
        if (dataSource == null) dataSource = Source.fromFile(new File("src/main/resources/testFiles/important_data.txt"))

        log.info("Just read some important data: " + dataSource.getLines().toList)
    }
  }

  val system = ActorSystem("BackoffSupervisorPattern")

  // val simpleActor = system.actorOf(Props[FileBasedPersistentActor], "simpleActor")
  // simpleActor ! ReadFile

  val simpleSupervisorProps = BackoffSupervisor.props(
    Backoff.onFailure(
      Props[FileBasedPersistentActor],
      "simpleBackoffActor",
      3 seconds,
      30 seconds,
      0.2
    )
  )

//  val simpleBackoffSupervisor = system.actorOf(simpleSupervisorProps, "simpleSupervisor")
//  simpleBackoffSupervisor ! ReadFile

  /*
    We created a simpleSupervisor
    - It creates a Child called simpleBackoffActor (props of type FileBasedPersistentActor). So we have FileBasedPersistentActor under simpleSupervisor as parent
    - `simpleSupervisor` will receive any message and will forward them to its child
    - Supervision Strategy is the default one [i.e. Restarting on everything]
      - when child fails, supervision strategy kicks in after 3 seconds [First Attempt]
      - When child fails again, the second attempt is 2X the previous attempt. So attempts will be 3s, 6s, 12s, 24s because Cap is 30s
        - The randomness factor adds a little bit of noise to this time so that we don't have a huge amount of actors starting off at that exact moment.

   */

  val stopSupervisorProps = BackoffSupervisor.props(
    Backoff.onStop( // Will kick in when child actor has stopped
      Props[FileBasedPersistentActor],
      "stopBackoffActor",
      3 seconds,
      30 seconds,
      0.2
    ).withSupervisorStrategy(
      OneForOneStrategy() {
        case _ => Stop // For any exception that is thrown the directive is to stop
      }
    )
  )

  //val simpleStopSupervisor = system.actorOf(stopSupervisorProps, "simpleStopSupervisor")

  // When `stopBackoffActor` throws an exception and the supervisor's strategy kicks in.
  // Which in case of anything it stops the backoff actor [stopBackoffActor].
  // And then after three seconds the `Backoff` kicks in and restarts the `stopBackoffActor` which is basically the same behavior that we saw before.
  // simpleStopSupervisor ! ReadFile

  class EagerFileBasedPersistentActor extends FileBasedPersistentActor {
    override def preStart(): Unit = {
      log.info("Eager actor starting")
      dataSource = Source.fromFile(new File("src/main/resources/testFiles/important_data.txt")) //<- `ActorInitializationException` is thrown
    }
  }

  // val eagerActor = system.actorOf(Props[EagerFileBasedPersistentActor]) //<- This throws `ActorInitializationException`

  // Default supervision strategy for all actors in case the exception is `ActorInitializationException` to stop.
  // So if a child actor ever throws an `ActorInitializationException` the default strategy is to stop it.

  val repeatedSupervisorProps = BackoffSupervisor.props(
    Backoff.onStop(
      Props[EagerFileBasedPersistentActor],
      "eagerActor",
      1 second,
      30 seconds,
      0.1
    )
  )

  val repeatedSupervisor = system.actorOf(repeatedSupervisorProps, "eagarSupervisor")

  // Above code will create `eagarSupervisor` and the child `eagerActor`
  // - `eagerActor` will die on start with `ActorInitializationException`
  // - It will trigger the supervision strategy in `eagarSupervisor` which is to stop `eagerActor`
  // - And then the backoff will kick in after 1 second because it kicks in on the stop of the child actor.
  //   - So the backoff will kick in after 1s,  then 2s,  then 4s, then 8s, 16s and that'll be it because the max cap is 30 seconds.
  // - So the `eagerActor` will continually die first and then after a second, 2s, 4s, 8s and 16s after which the backoff will stop kicking in and the `eagerActor` will stay dead.




}
