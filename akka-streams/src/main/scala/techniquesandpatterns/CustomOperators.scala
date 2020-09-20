package techniquesandpatterns

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, FlowShape, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Random, Success}

/**
  * Streams Lecture 23, 24 [Custom Operators with Graph Stages]
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13531000
  */

object CustomOperators extends App {

  implicit val system: ActorSystem = ActorSystem("CustomGraphShapes")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /*
    1 A custom source which emits random numbers until canceled
   */

  class RandomNumberGenerator(max: Int) extends GraphStage[/* step0 Define the shape*/SourceShape[Int]] {

    // step 1: define the ports and the component-specific members
    val outPort: Outlet[Int] = Outlet[Int]("randomGenerator")
    val random = new Random

    // Step 2: Construct a new shape
    override def shape: SourceShape[Int] = SourceShape(outPort)

    // Step 3: Create the logic

    // When graph is materialized `createLogic` will be called by Akka
    // And `GraphStageLogic` will be constructed
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // implement my logic here
      // This graph stage logic class is fully implemented so we don't need to override any methods
      // but we need to specify what should happen at the construction of this graph stage logic object.
      // In other words we just need to run some code inside and the kind of code that we need to run would be to set handlers on our ports of our components.

      // Step 4
      setHandler(outPort, new OutHandler {

        // This method will be called when there is demand from downstream
        // so whenever a consumer asks for a new element `onPull` will be called on my `OutHandler` that I attach to my outPort.
        // So here I get the chance to emit a new element
        override def onPull(): Unit = {
          // emit a new element
          val nextNumber = random.nextInt(max)

          // push it out of the outport
          push(outPort, nextNumber)
        }
      })


    }

  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
  // randomGeneratorSource.runWith(Sink.foreach(println))

  /*
    2 A Custom Sink that will prints elements in batches of given size
   */

  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {

    val inPort: Inlet[Int] = Inlet[Int]("batcher")

    override def shape: SinkShape[Int] = SinkShape[Int](inPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      // In `preStart` I'm going to be the first to ask for an element
      override def preStart(): Unit = {
        // pull is signaling a demand upstream for a new element
        pull(inPort)
      }

      // mutable state
      val batch = new mutable.Queue[Int]

      setHandler(inPort, new InHandler {

        // when upstream wants to send me an element
        override def onPush(): Unit = {
          val nextElement = grab(inPort)

          batch.enqueue(nextElement)

          if (batch.size >= batchSize) {
            println("New Batch: " + batch.dequeueAll(_ => true).mkString("[", ", ", "]"))
          }

          // Simulating back pressure
//          Thread.sleep(100)

          // Sending demand upstream
          pull(inPort)

        }

        // This method is called when stream terminates
        override def onUpstreamFinish(): Unit = {
          if (batch.nonEmpty) {
            println("New Batch: " + batch.dequeueAll(_ => true).mkString("[", ", ", "]"))
            println("Stream finish")
          }
        }
      })
    }
  }

  val batcherSink = Sink.fromGraph(new Batcher(10))
  randomGeneratorSource.to(batcherSink)
    //.run

  // So graph stage API with all this complex constructs gives us a lot of control over how we want these components to behave.
  // But the back pressure aspect of these components will still happen automatically.
  // So for example if batcher takes a long time to run the back pressure signal will automatically be sent to the random number generator and it will slow it down.

  // Also
  // Any mutable state goes inside `GraphStageLogic` because the create logic method is called on every single materialization of that component so any mutable state should stay in the graph stage logic.

  /**
    * Exercise
    *
    * Create Custom Flow
    * - A Simple filter flow
    */

  class SimpleFilter[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {

    val inPort: Inlet[T] = Inlet[T]("filterIn")
    val outPort: Outlet[T] = Outlet[T]("filterOut")

    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      setHandler(outPort, new OutHandler {
        // downstream asking an element
        override def onPull(): Unit = pull(inPort)
      })

      setHandler(inPort, new InHandler {
        override def onPush(): Unit = {
          try {
            val nextElement = grab(inPort)

            if (predicate(nextElement)) {
              push(outPort, nextElement)
            } else {
              pull(inPort) // asks for another element
            }
          } catch {
            case e: Throwable => failStage(e)
          }
        }
      })
    }
  }

  val myFilter = Flow.fromGraph(new SimpleFilter[Int](_ > 50))

  randomGeneratorSource.via(myFilter).to(batcherSink)
    //.run

  // Back Pressure works Out Of the box

  /**
    * Materialized values in graph Stages
    */

  // 4 FLow that counts the number of elements that go through it

  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {

    val inPort: Inlet[T] = Inlet[T]("counterIn")
    val outPort: Outlet[T] = Outlet[T]("counterOut")

    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {

      val promise = Promise[Int]
      val logic: GraphStageLogic = new GraphStageLogic(shape) {

        // setting Mutable state
        var counter = 0

        setHandler(outPort, new OutHandler {
          override def onPull(): Unit = pull(inPort)

          override def onDownstreamFinish(): Unit = {
            promise.success(counter)
            super.onDownstreamFinish()
          }
        })

        setHandler(inPort, new InHandler {
          override def onPush(): Unit = {
            val nextElement = grab(inPort)
            counter += 1

            // pass it on
            push(outPort, nextElement)
          }

          override def onUpstreamFinish(): Unit = {
            promise.success(counter)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }

        })


      }

      (logic, promise.future)
    }
  }

  val counterFlow = Flow.fromGraph(new CounterFlow[Int])

  val countFuture = Source(1 to 10)
    // .map(x => if(x == 7) throw new RuntimeException else x) // Simulating Exception
    .viaMat(counterFlow)(Keep.right)
    .to(Sink.foreach[Int](println))
    //.to(Sink.foreach[Int](x => if(x == 7) throw new RuntimeException("Gotcha Sink") else println(x))) // Simulating Downstream Failure
    .run

  import system.dispatcher
  countFuture.onComplete {
    case Success(count) => println(s"The number of elements passed: $count")
    case Failure(exception) => println(s"Counting the elements failed: $exception")
  }

  // Notes
  // - The callbacks on push and on pull are never ever called concurrently so we can safely access mutable state inside these handlers.
  // - We should never ever expose mutable state outside these handlers for example in future onComplete callbacks
  //  because this can break the component encapsulation in much the same way as we can break actor encapsulation if you remember.

}
