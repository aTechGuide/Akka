package lowlevelserver

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Akka HTTP Lecture 6 [Low Level Server API]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-http/learn/lecture/14128297
  */

object LowLevelAPI extends App {

  implicit val system: ActorSystem = ActorSystem("LowLevelServerAPI")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http().bind("localhost", 8000 )
  // ^ Source of Connections with Materialized value of Future[Http.ServerBinding] which will allow us to unbind or shut down the server

  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted Incoming Connection from: ${connection.remoteAddress}")
  }

  // In order to actually start the server we need to materialize a RunnableGraph from `serverSource` to  `connectionSink`

  val serverBindingFuture: Future[Http.ServerBinding] = serverSource.to(connectionSink).run()
  serverBindingFuture.onComplete {
    case Success(binding) =>
      println("Server binding successful")
      // binding.terminate(10 seconds)
    case Failure(exception) => println(s"Server binding Failed $exception")
  }

  /*
    Method 1: Synchronously server HTTP Responses
   */
  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) => // method, URI, HTTP Headers, Content and the protocol (HTTP1.1 / HTTP 2.0)
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity( // Payload
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP
            | </body>
            |</html>
          """.stripMargin
      )
    )

    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // HTTP 404
        entity = HttpEntity( // Payload
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   OOPS ! The resource can NOT be Found
            | </body>
            |</html>
          """.stripMargin
        )
      )
  }

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

  // val value: Future[Done] = Http().bind("localhost", 8080).runWith(httpSyncConnectionHandler)
  // ^ It automatically starts and runs a Runnable Graph.
  // So remember `Http().bind("localhost", 8080)` is a source of incoming connection.
  // And `httpSyncConnectionHandler` is a sink of incoming connection.
  // So a source run with a sink is actually Akka stream.

  // Short Hand version of above
  // Http().bindAndHandleSync(requestHandler, "localhost", 8080)

  /*
    Method 2: Server back HTTP responses ASYNCHRONOUSLY
    - Meaning that I'm returning the HTTP response asynchronously i.e. on some other thread
   */

  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => // method, URI, HTTP Headers, Content and the protocol (HTTP1.1 / HTTP 2.0)
      Future(HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity( // Payload
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP
            | </body>
            |</html>
          """.stripMargin
        )
    ))

    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.NotFound, // HTTP 404
        entity = HttpEntity( // Payload
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   OOPS ! The resource can NOT be Found
            | </body>
            |</html>
          """.stripMargin
        )
      ))
  }

  val httpAsyncConnectionHandler: Sink[Http.IncomingConnection, Future[Done]] = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }
  // ^ This is useful when it takes a while to respond to an HTTP request with an HTTP response.
  // For example if we're doing server side rendering or something like that. It may help with parallelism

  // Stream-based "manual" version
  // Http().bind("localhost", 8081).runWith(httpAsyncConnectionHandler)

  // ShortHand
  Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8081)

  /*
    Method 3: ASYNCHRONOUSLY via Akka Streams
   */

  val streamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => // method, URI, HTTP Headers, Content and the protocol (HTTP1.1 / HTTP 2.0)
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity( // Payload
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP
            | </body>
            |</html>
          """.stripMargin
        )
      )

    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // HTTP 404
        entity = HttpEntity( // Payload
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   OOPS ! The resource can NOT be Found
            | </body>
            |</html>
          """.stripMargin
        )
      )
  }

  // "manual" version
//  Http().bind("localhost", 8082).runForeach { incomingConnection =>
//    incomingConnection.handleWith(streamsBasedRequestHandler)
//  }

  // Short Hand Version
  Http().bindAndHandle(streamsBasedRequestHandler, "localhost", 8082)

}
