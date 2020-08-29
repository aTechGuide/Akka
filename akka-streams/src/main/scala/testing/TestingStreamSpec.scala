package testing

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Streams Lecture 19 [Testing Akka Streams]
  *
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530976
  */

class TestingStreamSpec extends TestKit(ActorSystem("TestingAkkaStreams"))
 with WordSpecLike with BeforeAndAfterAll {

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A Simple Stream" should {
    "satisfy basic assertions" in {

      val simpleSource = Source(1 to 10)
      val simpleSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((a, b) => a + b)

      val sumFuture = simpleSource.toMat(simpleSink)(Keep.right).run
      val sum = Await.result(sumFuture, 2 seconds)

      assert(sum == 55)
    }

    "integrate with test actors via materialized values" in {

      import akka.pattern.pipe
      import system.dispatcher

      val simpleSource = Source(1 to 10)
      val simpleSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((a, b) => a + b)

      val probe = TestProbe()

      simpleSource.toMat(simpleSink)(Keep.right).run().pipeTo(probe.ref)

      probe.expectMsg(55)
    }

    "integrate with a test actor based sink" in {

      val simpleSource = Source(1 to 5)
      val flow = Flow[Int].scan[Int](0)(_ + _) // 0, 1, 3, 6, 10, 15
      val streamUnderTest = simpleSource.via(flow)

      val probe = TestProbe()
      val probeSink = Sink.actorRef(probe.ref, "completion message")

      streamUnderTest.to(probeSink).run
      probe.expectMsgAllOf(0, 1, 3, 6, 10, 15)

    }

    "integrate with Streams TestKit Sink" in {
      val sourceUnderTest = Source(1 to 5).map(_ * 2)

      val testSink = TestSink.probe[Int]
      val materializedTestValue: TestSubscriber.Probe[Int] = sourceUnderTest.runWith(testSink)

      materializedTestValue
        .request(5)
        .expectNext(2, 4, 6, 8, 10)
        .expectComplete()
    }

    "integrate with Streams TestKit Source" in {
      val sinkUnderTest: Sink[Int, Future[Done]] = Sink.foreach[Int] {
        case 13 => throw new RuntimeException
        case _ =>
      }

      val testSource: Source[Int, TestPublisher.Probe[Int]] = TestSource.probe[Int]
      val materialized: (TestPublisher.Probe[Int], Future[Done]) = testSource.toMat(sinkUnderTest)(Keep.both).run

      val (testPublisher, resultFuture) = materialized

      testPublisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(13)
        .sendComplete()

      import system.dispatcher
      resultFuture.onComplete {
        case Success(_) => fail("The sink under test should have thrown an exception on 13")
        case Failure(_) => // OK
      }
    }

    "test flows with a test source and a test sink" in {
      val flowUnderTest = Flow[Int].map(_ * 2)

      val testSource = TestSource.probe[Int]
      val testSink = TestSink.probe[Int]

      val materialized = testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run
      val (publisher, subscriber) = materialized

      publisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(42)
        .sendNext(99)
          .sendComplete()

      subscriber
        .request(4)
        .expectNext(2, 10, 84, 198)
        .expectComplete()
    }

  }

}
