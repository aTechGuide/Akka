package techniquesandpatterns

import java.util.Date

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.concurrent.Future


/**
  * Streams Lecture 16 [Integrating With External Services]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530956#content
  */

object IntegratingWithExternalServices extends App {

  implicit val system: ActorSystem = ActorSystem("IntegratingWithExternalServices")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // import system.dispatcher // NOT recommended in practice for mapAsync
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericExternalService[A, B](element: A): Future[B] = ???

  // Example: Simplified Pager Duty
  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource: Source[PagerEvent, NotUsed] = Source(List(
    PagerEvent("AkkaInfra", "InfraBroke", new Date),
    PagerEvent("FastDataPipeline", "Illegal Data", new Date),
    PagerEvent("AkkaInfra", "Service stopped responding", new Date),
    PagerEvent("SuperFrontEnd", "A button does NOT work", new Date)
  ))

  object PagerService {
    private val engineers = List("Daniel", "John", "Lady")
    private val emails = Map(
      "Daniel" -> "daniel@rtjvm.com",
      "John" -> "john@rtjvm.com",
      "Lady" -> "lady@rtjvm.com"
    )

    def processEvent(pagerEvent: PagerEvent) = Future {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 36000)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)

      engineerEmail
    }
  }

  val infraEvents: Source[PagerEvent, NotUsed] = eventSource.filter(_.application == "AkkaInfra")

  // And for `infraEvents` I'd like to notify the engineer on call for that event.
  // So the problem is that we cannot really use map or use a regular Flow on these events because our external service can only return a future

  // parallelism determines how many futures can run at the same time and if one of those future fails the whole stream will fail.
  val pagedEngineerEmails: Source[String, NotUsed] = infraEvents.mapAsync(parallelism = 4)(event => PagerService.processEvent(event))

  // Now because we are running multiple futures at the same time depending on how many elements we have in our stream
  // There is the question of the Order of the output elements in the stream
  // and the mappings sync guarantees the relative order of elements regardless of which future is faster or slower
  // the output elements are always going to maintain the relative order of the input elements.

  // If we don't need this relative order guarantee there is also a variance of `mapAsync` called `mapAsyncUnordered`
  // which does the same thing as mapAsync except it doesn't guarantee the relative order of the output elements.

  // If we don't require the ordering guarantees then `mapAsyncUnordered` version is even faster.
  // There are some perf considerations when using mapAsync because due to this guarantee mapAsync has to always wait for the futures to complete so that you can keep its order.
  // So if one of the future is that you run here under map a sink a slow that will slow down the entire stream.
  // The futures are still evaluated in parallel but mapAsync always waits for the future that is supposed to give the next element even if later futures execute faster.

  val pagedEmailsSink = Sink.foreach[String](email => println(s"Successfully sent notification to $email"))
//  pagedEngineerEmails.to(pagedEmailsSink).run()


  // Actor Service
  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Daniel", "John", "Lady")
    private val emails = Map(
      "Daniel" -> "daniel@rtjvm.com",
      "John" -> "john@rtjvm.com",
      "Lady" -> "lady@rtjvm.com"
    )

    private def processEvent(pagerEvent: PagerEvent): String = {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 36000)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      log.info(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)

      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

  import akka.pattern.ask
  import scala.concurrent.duration._

  implicit val timeout: Timeout = Timeout(3 seconds)
  val pagerActor = system.actorOf(Props[PagerActor], "pagerActor")
  val alternativePagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])

  alternativePagedEngineerEmails.to(pagedEmailsSink).run()

  // DO NOT Confuse mapAsync with async (ASYNC boundary)

}
