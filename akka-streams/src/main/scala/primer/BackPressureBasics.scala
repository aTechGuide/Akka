package primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import scala.concurrent.duration._

/**
  * Streams Lecture 8 [Back Pressure]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530914#overview
  */

object BackPressureBasics extends App {

  implicit val system: ActorSystem = ActorSystem("OperatorFusion")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>

    // Simulating a long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

  fastSource.to(slowSink)//.run() // <- This is Fusing [i.e. everything runs on same Actor Instance]
  // NOT BACK PRESSURE


  // BACK PRESSURE
  fastSource.async.to(slowSink)//.run()

  // If we have flows in our stream, the back pressure signal is going to get propagated through them as well.
  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming: $x")
    x + 1
  }

  fastSource.async
    .via(simpleFlow).async
    .to(slowSink)
    //.run()

  // Explanation
  // - The slow sync because it's slow it sends back pressure signals upstream.
  // - Now the simple flow when it receives that back pressure `signal` instead of forwarding that back pressure signal to the source
  //   - It buffers internally a number of elements until it's had enough.
  //   - The default buffer in all streams is 16 elements.
  // - When the internal buffer inside the simple flow was full. Simple flow had no choice but to send a back pressure signal to the fast source and wait for some more incoming demand from the sync.
  // - So after the sync process some elements the simple flow allowed more elements to go through and it buffered them again because the sync is still slow and this happens a bunch of times.

  // An Akka streams component can have multiple reactions to back pressure signals.
  // Buffering [which is what we're seeing here] is one of them

  // A component will react to back pressure in the following way
  // 1 It will try to slow down if possible
  //   - In the first example with the fast source to slow sync that was possible because fast source could slow down the production of its elements
  //   - But in second case, the `simpleFlow` cannot do that because it simply cannot control the rate at which it receives elements
  // 2 It will try to buffer elements which is clearly what's happening in second example until there's more demand
  // 3 The next reaction is to drop down elements from the buffer if it overflows until there's more demand.
  // 4 Finally the last resort is to tear down / kill the whole stream. That is also known as a failure.

   // We as Akka streams programmers can only control what happens to a component at 3rd point
  // - if the component has buffered enough elements and it's about to overflow.

  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)
  // This is an enhancement of the original flow with a buffer or with buffer parameters. `dropHead` will drop the oldest element in the buffer to make room for the new one.
  fastSource.async
    .via(bufferedFlow).async
    .to(slowSink)
    //.run()

  // Explanation
  // - Notice that the flow prints incoming from 1 to 1000. So it receives all the 1000 elements from the source.
  // - Now because I set the buffer to 10 and the overflow strategy to dropHead which drops the oldest elements in the buffer.
  // - Most of these 1000 elements were dropped and the elements that got to the sink were numbers 2 to 17 and numbers 992 to 1001.
  // - Now the last 10 elements are easy to explain because they are the newest elements that exit the flow
  //   - so the numbers that exit the flow are the ones processed from numbers 991 to 1000.
  // - So because we applied the overflow strategy dropHead only these last 10 numbers ended up being buffered in the flow.
  //
  // Now the last piece of the explanation is the printing of the first 16 numbers in the sink.
  // - The reason why these numbers are also printed out is because these numbers were buffered at the sink
  // - The sink buffers the first 16 numbers and the flow buffers the last 10 numbers which finally end up in the sink.

  // Conclusion
  // - For the numbers 1 to 16 nobody's backed pressured because the sink buffers the first 16 results because it's so slow
  // - Then the next 10 elements the number 17 to 26 will be buffered in the flow
  // - At that point because the source is so fast and amidst the first 26 elements in a blink of an eye the sink doesn't even have the chance to print the first result.
  // - So the flow will start dropping out the next element which will do because 1000 elements are also emitted very very quickly.
  // - So the numbers 26 to 1000 flow will always drop the oldest element
  // - So the contents of the buffer of the flow will be the numbers 991 to 1000 which will eventually be transformed into 992 to 1001 which will be then admitted to the sink eventually.
  // - Now because the sink is so slow it will process every single one of the elements in its buffer One at a time one per second.
  // - And it will then process the other elements nine hundred ninety two to 1001.

  /*
    Overflow Strategies
    - dropHead = drop Oldest
    - dropTail = drop newest element added to buffer
    - dropNew = drop the exact element to be added = keep the buffer
    - drop the entire buffer
    - Emit back pressure signal
    - Fail
   */

  /*
    We have a back pressure centric method on Akka streams to manually trigger back pressure and that is called Throttling
   */
  fastSource.throttle(2, 1 second) // Composite Component that emits 2 elements per second
    .runWith(Sink.foreach(println))

}
