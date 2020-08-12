package persistence.primer

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

/**
  * Persistence Lecture 8 [Persistence Actors: Exercise]
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002428
  */

object PersistentActorsExercise extends App {

  /*
    Design Persistent Actors for Voting Systems

    keep:
    - The citizen who voted
    - Pool Mapping between a candidate and # of received votes

    The actor must be able to recover its state if its shutdown or restarted
   */

  // COMMANDS
  case class Vote(citizenID: String, candidate: String)

  // EVENT
  case class VoteRecorded(citizenID: String, candidate: String)

  class VotingStation extends PersistentActor with ActorLogging {

    val citizens: mutable.Set[String] = new mutable.HashSet[String]()
    val poll: mutable.Map[String, Int] = new mutable.HashMap[String, Int]()

    override def persistenceId: String = "simple-voting-station"

    override def receiveCommand: Receive = {
      case vote @ Vote(citizenID, candidate) =>

        if (!citizens.contains(vote.citizenID)) {

          // COMMAND Sourcing: As we are directly persisting a command
          persist(vote) { _ =>

            log.info(s"Persisted: $vote")
            handleInternalStateChange(citizenID, candidate)
          }
        } else {
          log.warning(s"Citizen $citizenID is trying to vote multiple times")
        }

      case "print" =>
        log.info(s"Current state: $poll")

    }

    override def receiveRecover: Receive = {
      case vote @ Vote(citizenID, candidate) =>

        log.info(s"Recovered $vote")
        handleInternalStateChange(citizenID, candidate)
    }

    def handleInternalStateChange(citizenID: String, candidate: String): Unit = {

      citizens.add(citizenID)
      val votes = poll.getOrElse(candidate, 0)
      poll.put(candidate, votes + 1)

    }

  }

  val system = ActorSystem("PersistentActorsExercise", ConfigFactory.load("application-persistance.conf"))
  val voteStation = system.actorOf(Props[VotingStation], "voteStation")

  val votesMap = Map[String, String](
    "Alice" -> "Martin",
    "Bob" -> "Roland",
    "Charlie" -> "Martin",
    "David" -> "Jonas",
    "Daniel" -> "Martin"
  )

  votesMap.keys.foreach { citizen =>
    voteStation ! Vote(citizen, votesMap(citizen))
  }

  // When Application restarts
  // - First Recovery happens
  // - New Messages are handled
  voteStation ! "print"



}
