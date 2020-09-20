package techniquesandpatterns

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, Graph, Inlet, Outlet, Shape, SinkShape, UniformFanInShape, UniformFanOutShape}

import scala.collection.immutable
import scala.language.postfixOps

/**
  * Streams Lecture 22 [Custom Graph Shapes]
  *
  * Creating components with an arbitrary number of inputs and outputs and of arbitrary types.
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530994#overview
  */

object CustomGraphShapes extends App {

  implicit val system: ActorSystem = ActorSystem("CustomGraphShapes")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // balance 2x3 shape
  case class Balance2x3 (
                   in0: Inlet[Int],
                   in1: Inlet[Int],
                   out0: Outlet[Int],
                   out1: Outlet[Int],
                   out2: Outlet[Int]
                   ) extends Shape {

    // Rhese inlets and outlets methods need to return a very well-defined order of inputs and outputs
    override def inlets: immutable.Seq[Inlet[_]] = List(in0, in1)

    override def outlets: immutable.Seq[Outlet[_]] = List(out0, out1, out2)

    override def deepCopy(): Shape = Balance2x3(
      in0.carbonCopy(),
      in1.carbonCopy(),
      out0.carbonCopy(),
      out1.carbonCopy(),
      out2.carbonCopy()
    )
  }

  val balance2x3Impl: Graph[Balance2x3, NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val merge: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))
    val balance: UniformFanOutShape[Int, Int] = builder.add(Balance[Int](3))

    merge ~> balance

    Balance2x3(
      merge.in(0),
      merge.in(1),
      balance.out(0),
      balance.out(1),
      balance.out(2)
    )
  }

  val balance2x3Graph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      import scala.concurrent.duration._
      val slowSource = Source(Stream.from(1)).throttle(1, 1 second)
      val fastSource = Source(Stream.from(1)).throttle(1, 2 second)

      def createSink(index: Int) = Sink.fold[Int, Int](0)((count, element) => {
      println(s"[Sink $index] Received $element, current count is $count")
      count + 1
    })

      val sink1: SinkShape[Int] = builder.add(createSink(1))
      val sink2: SinkShape[Int] = builder.add(createSink(2))
      val sink3: SinkShape[Int] = builder.add(createSink(3))

      val balance2x3: Balance2x3 = builder.add(balance2x3Impl)

      slowSource ~> balance2x3.in0
      fastSource ~> balance2x3.in1

      balance2x3.out0 ~> sink1
      balance2x3.out1 ~> sink2
      balance2x3.out2 ~> sink3

      ClosedShape
    }
  )

//  balance2x3Graph.run

  /**
    * Generalize the balance component M x N
    */

  case class BalanceMxN[T](
                          override val inlets: List[Inlet[T]],
                          override val outlets: List[Outlet[T]]
                          ) extends Shape {

    override def deepCopy(): Shape = BalanceMxN(inlets.map(_.carbonCopy()), outlets.map(_.carbonCopy()))
  }

  object BalanceMxN {
    def apply[T](inputCount: Int, outputCount: Int): Graph[BalanceMxN[T], NotUsed] =
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val merge: UniformFanInShape[T, T] = builder.add(Merge[T](inputCount))
        val balance: UniformFanOutShape[T, T] = builder.add(Balance[T](outputCount))

        merge ~> balance

        BalanceMxN(
          merge.inlets.toList,
          balance.outlets.toList
        )
      }
  }

  val balanceMxNGraph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      import scala.concurrent.duration._
      val slowSource = Source(Stream.from(1)).throttle(1, 1 second)
      val fastSource = Source(Stream.from(1)).throttle(1, 2 second)

      def createSink(index: Int) = Sink.fold[Int, Int](0)((count, element) => {
        println(s"[Sink $index] Received $element, current count is $count")
        count + 1
      })

      val sink1: SinkShape[Int] = builder.add(createSink(1))
      val sink2: SinkShape[Int] = builder.add(createSink(2))
      val sink3: SinkShape[Int] = builder.add(createSink(3))

      val balance2x3 = builder.add(BalanceMxN[Int](2, 3))

      slowSource ~> balance2x3.inlets(0)
      fastSource ~> balance2x3.inlets(1)

      balance2x3.outlets(0) ~> sink1
      balance2x3.outlets(1) ~> sink2
      balance2x3.outlets(2) ~> sink3

      ClosedShape
    }
  )

  balanceMxNGraph.run

}
