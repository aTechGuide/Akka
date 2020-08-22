package graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape, FlowShape, Graph, SinkShape, SourceShape}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}

/**
  * Streams Lecture 13 [Bidirectional Flows]
  *
  * - Flows that go both ways
  *   - From A to B
  *   - From B to A
  *
  * Then the composite component that takes two of these flows is called a bi directional flow
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-streams/learn/lecture/13530950
  */

object BidirectionalFlows extends App {

  implicit val system: ActorSystem = ActorSystem("BidirectionalFlows")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /*
    Example: cryptography
   */

  // `encrypt` takes a number and a string and translates each of the characters of the `string` by `n` amount
  def encrypt(n: Int)(string: String): String = string.map(c => (c + n).toChar)
  def decrypt(n: Int)(string: String): String = string.map(c => (c - n).toChar)

  // bidiFlow
  val bidiCryptoStaticGraph: Graph[BidiShape[String, String, String, String], NotUsed] =
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>

    val encryptionFlowShape: FlowShape[String, String] = builder.add(Flow[String].map(elem => encrypt(3)(elem)))
    val decryptionFlowShape: FlowShape[String, String] = builder.add(Flow[String].map(decrypt(3)))

    // BidiShape(encryptionFlowShape.in, encryptionFlowShape.out, decryptionFlowShape.in, decryptionFlowShape.out)
    // Shorthand Notation
    BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
  }

  val unencryptedStrings = List("Akka", "is", "awsome")
  val unencryptedSource: Source[String, NotUsed] = Source(unencryptedStrings)
  val encryptedSource = Source(unencryptedStrings.map(encrypt(3)))

  val cryptoBidiGraph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val unencryptedSourceShape: SourceShape[String] = builder.add(unencryptedSource)
      val encryptedSourceShape: SourceShape[String] = builder.add(encryptedSource)
      val bidi: BidiShape[String, String, String, String] = builder.add(bidiCryptoStaticGraph)

      val encryptedSinkShape: SinkShape[String] = builder.add(Sink.foreach[String](string => println(s"Encrypted $string")))
      val decryptedSinkShape: SinkShape[String] = builder.add(Sink.foreach[String](string => println(s"Decrypted $string")))

      unencryptedSourceShape ~> bidi.in1  ;  bidi.out1 ~> encryptedSinkShape
   // encryptedSourceShape   ~> bidi.in2  ;  bidi.out2 ~> decryptedSinkShape
      // Alternative way of writing
      decryptedSinkShape    <~ bidi.out2  ;  bidi.in2 <~ encryptedSourceShape

      ClosedShape
    }
  )

  cryptoBidiGraph.run()

  // We often use these kind of components in practice for reversible operations like for example
  // - encrypting and decrypting
  // - encoding or decoding
  // - serializing or deserialize.

  // So whenever you have some kind of reversible operation that you want to put into an Akka stream think of a bidirectional flow.

}
