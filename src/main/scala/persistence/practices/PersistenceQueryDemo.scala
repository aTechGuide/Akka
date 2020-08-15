package persistence.practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Random

/**
  * Persistence Lecture 19 [Persistence Query]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002480#overview
  */

object PersistenceQueryDemo extends App {

  val system = ActorSystem("PersistenceQuery", ConfigFactory.load("application-persistence.conf").getConfig("persistenceQuery"))

  // Read Journal
  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // Get me all the persistence ideas that are available in the journal [Infinite Stream]
  val persistenceIds = readJournal.persistenceIds()

  // [Finite Stream]
  // val persistenceIds = readJournal.currentPersistenceIds();

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
//  persistenceIds.runForeach { persistenceId =>
//    println(s"Found Persistence ID $persistenceId")
//  }

  class SimplePersistentActor extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case e => {
        log.info(s"Recovered $e")
      }
    }

    override def receiveCommand: Receive = {
      case m => persist(m) { _ =>
        log.info(s"Persisted $m")
      }
    }

    override def persistenceId: String = "persistent-query-id-1"
  }


  val simpleActor = system.actorOf(Props[SimplePersistentActor], "simpleActor")

  import system.dispatcher
  system.scheduler.scheduleOnce(5 seconds) {
    val message = "Hello, Actor"
    simpleActor ! message
  }

  /**
    * Events by Persistence ID
    */

  val events = readJournal.eventsByPersistenceId("persistent-query-id-1", 0, Long.MaxValue)
  events.runForeach { event =>
    println(s"Read event: $event")
  }

  /**
    * Events by Tags
    * - Allows us to query for events across multiple persistence IDs
    */

  val genres = Array("pop", "rock", "jazz", "disco", "hip-hop")
  case class Song(artist: String, title: String, genre: String)

  // Command
  case class Playlist(songs: List[Song])

  // Event
  case class PlaylistPurchased(id: Int, songs: List[Song])

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {

    var latestPlaylistId = 0

    override def receiveRecover: Receive = {
      case event @ PlaylistPurchased(id, _) => {
        log.info(s"Recovered: $event")
        latestPlaylistId = id
      }
    }

    override def receiveCommand: Receive = {
      case Playlist(songs) =>
        persist(PlaylistPurchased(latestPlaylistId, songs)) { _ =>
          log.info(s"User Purchased: $songs")
          latestPlaylistId += 1
        }
    }

    override def persistenceId: String = "music-store-checkout"
  }

  class MusicStoreEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = "musicStore"

    override def toJournal(event: Any): Any = event match {
      case event @ PlaylistPurchased(_, songs) =>
        val genres = songs.map(_.genre).toSet

        // Tagged is a wrapper for an event that also adds a set of strings to it.
        Tagged(event, genres)

      case event => event

    }
  }

  val checkoutActor = system.actorOf(Props[MusicStoreCheckoutActor], "checkoutActor")

  val r = new Random
  for (_ <- 1 to 10) {
    val maxSongs = r.nextInt(5)
    val songs = for (i <- 1 to maxSongs) yield {
      val randomGenre = genres(r.nextInt(5))
      Song(s"Artist $i", s"My Song $i", randomGenre)
    }

    checkoutActor ! Playlist(songs.toList)
  }


  // Note:
  // - `eventsByPersistenceId` will give you back the events in the same order that they were persistent
  // - `eventsByTag` because it searches across persistence IDs, will not guarantee the order in which you receive the event
  val rockPlayList = readJournal.eventsByTag("rock", Offset.noOffset)
  rockPlayList.runForeach { event =>
    println(s"Found a playlist with a rock song: $event")
  }

}
