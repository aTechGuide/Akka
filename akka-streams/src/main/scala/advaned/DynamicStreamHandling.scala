package advaned

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, Graph, KillSwitches, SharedKillSwitch, UniqueKillSwitch}

/**
  * Streams Lecture 20 [Dynamic Stream Handling]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530984#overview
  */

object DynamicStreamHandling extends App {

  implicit val system: ActorSystem = ActorSystem("DynamicStreamHandling")
  implicit val materializer: ActorMaterializer = ActorMaterializer()


  // 1 Kill Switch [how to dynamically stop or aboard a stream on purpose]
  // kill switch is a special kind of flow that emits the same elements that go through it but it materializes to a special value that has some additional methods.

  val killSwitchFlow: Graph[FlowShape[Int, Int], UniqueKillSwitch] = KillSwitches.single[Int]

  import scala.concurrent.duration._
  val counter = Source(Stream.from(1)).throttle(1, 1 second).log("counter")
  val sink = Sink.ignore

  val killSwitch: UniqueKillSwitch = counter
    .viaMat(killSwitchFlow)(Keep.right)
    .toMat(sink)(Keep.left)
    .run

  import system.dispatcher
  system.scheduler.scheduleOnce(3 seconds) {
    killSwitch.shutdown()
  }

  // Killing Multiple Streams
  val anotherCounter1 = Source(Stream.from(1)).throttle(1, 1 second).log("anotherCounter1")
  val anotherCounter2 = Source(Stream.from(1)).throttle(2, 1 second).log("anotherCounter2")
  val sharedKillSwitch: SharedKillSwitch = KillSwitches.shared("oneButtonToRuleThemAll")

  anotherCounter1.via(sharedKillSwitch.flow).runWith(Sink.ignore)
  anotherCounter2.via(sharedKillSwitch.flow).runWith(Sink.ignore)

  system.scheduler.scheduleOnce(3 seconds) {
    sharedKillSwitch.shutdown() //<- It will shutdown both streams
  }

  // Merge Hub

  val dynamicMerge: Source[Int, Sink[Int, NotUsed]] = MergeHub.source[Int]
  val materializedSink: Sink[Int, NotUsed] = dynamicMerge.to(Sink.foreach[Int](println)).run

  // This `materializedSink` can be used in any other graphs.
  // So this is how we can dynamically at runtime add virtual fan in inputs to the same consumer (sink)
  // So we can run any number of graphs with this `materializedSink` thus materializing it's any number of times
  Source(1 to 10).runWith(materializedSink)

  // Broadcast Hub
  val dynamicBroadcast:  Sink[Int, Source[Int, NotUsed]] = BroadcastHub.sink[Int]

  // Because the dynamic broadcast materializes to a source
  // Then I can plug the source any number of times in any number of graphs to any number of components
  val materializedSource: Source[Int, NotUsed] = Source(1 to 100).runWith(dynamicBroadcast)

  materializedSource.runWith(Sink.ignore)
  materializedSource.runWith(Sink.foreach[Int](println))


  /**
    * Combine MergeHub and broadcastHub
    *
    * - Its a publisher-subscriber component in that we can dynamically add sources and sinks to this component
    * - Every single element produced by every single source will be known by every single subscriber
    */

  val merge: Source[String, Sink[String, NotUsed]] = MergeHub.source[String]
  val broadcast: Sink[String, Source[String, NotUsed]] = BroadcastHub.sink[String]

  val materializedValues: (Sink[String, NotUsed], Source[String, NotUsed]) = merge.toMat(broadcast)(Keep.both).run()
  val (publisherPort, subscriberPort) = materializedValues


  Source(List("Akka", "is", "amazing")).runWith(publisherPort)
  Source(List("I", "love", "scala")).runWith(publisherPort)
  Source.single("STREEEMS").runWith(publisherPort)

  subscriberPort.runWith(Sink.foreach(e => println(s" I received the $e")))
  subscriberPort.map(_.length).runWith(Sink.foreach(n => println(s" I got a number $n")))



}
