package techniquesandpatterns

import java.util.Date

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.language.postfixOps

/**
  * Streams Lecture 17 [Advance Backpressure]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530960
  */

object AdvanceBackpressure extends App {

  implicit val system: ActorSystem = ActorSystem("AdvanceBackpressure")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // dropHead will drop the oldest element from the buffer to make room for the new element
  // dropTail will drop the newest element to make room for the incoming element
  // dropNew will drop the incoming element
  // dropBuffer will drop the entire buffer
  // fail will just tear down the entire stream
  // backpressure will send a signal upstream

  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  // Pager Service
  case class PagerEvent(description: String, date: Date, nInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("Service discovery fail", new Date),
    PagerEvent("Illegal elements in data pipeline", new Date),
    PagerEvent("# of HTTP 500 Spike", new Date),
    PagerEvent("Service Stopped Responding", new Date)
  )

  val eventSource: Source[PagerEvent, NotUsed] = Source(events)

  val oncallEngineer = "kamran@atech.guide" // a fast service to fetching on call engineer

  def sendEmail(notification: Notification): Unit = println(s"Dear ${notification.email} you have an event ${notification.pagerEvent}")

  val notificationSink: Sink[PagerEvent, NotUsed] = Flow[PagerEvent].map(event => Notification(oncallEngineer, event))
    .to(Sink.foreach[Notification](sendEmail))

  // Standard
  // eventSource.to(notificationSink).run()

  /*s
    Un Backpreassurable Source
   */
  def sendEmailSlow(notification: Notification): Unit = {
    Thread.sleep(1000)
    println(s"Dear ${notification.email} you have an event ${notification.pagerEvent}")
  }

  // So if the source cannot be backed pressured for some reason a solution to that is
  // to somehow aggregate the incoming events and create one single notification when we receive demand from the sync.

  // So instead of buffering the events on our Flow and creating three notifications for three different events
  // we can create a single notification for multiple events
  // Hence, we can aggregate the pager events and create a single notification out of them
  // and for that we will use a method called `conflate`.

  // `conflate` acts like fold in that it combines elements but it emits the result only when the downstream sends demand.
  val aggregateNotificationFlow = Flow[PagerEvent]
    .conflate((event1, event2) => {
      val nInstances = event1.nInstances + event2.nInstances
      PagerEvent(s"You've $nInstances eveents that require your attention", new Date, nInstances)
    })
    .map(resultingEvent => Notification(oncallEngineer, resultingEvent))

  // events were coming so fast that flow aggregated the incoming events.
  // Also, `conflate` never backed pressured hence wee where decoupling the upstream rate from the downstream rate.
  // So this is an alternative to back pressure
  eventSource.via(aggregateNotificationFlow)
    .async
    .to(Sink.foreach[Notification](sendEmailSlow))
    //.run()

  /*
    Dealing with Slow Producers
    - extrapolate/expand
   */

  import scala.concurrent.duration._
  val slowCounter = Source(Stream.from(1)).throttle(1, 1 second)
  val hungrySink = Sink.foreach[Int](println)

  // So in case there is unmet demand from downstream.
  // This iterator will start producing more elements and artificially feed them downstream.
  val extrapolator: Flow[Int, Int, NotUsed] = Flow[Int].extrapolate(element => Iterator.from(element))
  val repeater: Flow[Int, Int, NotUsed] = Flow[Int].extrapolate(element => Iterator.continually(element))

  // So with every new element because it takes so long to admit the next number
  // extrapolates starts producing more elements which are quite a lot in one second.
  // slowCounter.via(extrapolator).to(hungrySink).run

  // we will see the same number over and over again.
  slowCounter.via(repeater).to(hungrySink).run

  // Expand
  // `expand` works the same way as an extrapolation with a twist.
  // So while the extrapolate creates the iterator only when there is unmet demand
  // the expand method creates the iterator at all times.
  val expandeer = Flow[Int].expand(element => Iterator.continually(element))

}
