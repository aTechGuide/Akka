package lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.stream.ActorMaterializer

import scala.concurrent.Future

/**
  * Akka HTTP Lecture 7 [Low Level Server API Exercise]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-http/learn/lecture/14128301
  */

object LowLevelAPIExercise extends App {

  implicit val system: ActorSystem = ActorSystem("LowLevelServerAPI")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /**
    * - Create HTTP Server which replies with following
    * - with welcome message on the "front door"
    * - with a proper HTML in localhost:8388/about
    * - with 404 message otherwise
    */

  val syncHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(
        // status code OK is default
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          "Hello from the front door"
        )
      )

    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) =>
      HttpResponse(
        // status code OK is default
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   My About Page
            | </body>
            |</html>
          """.stripMargin
        )
      )

    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      HttpResponse(
        StatusCodes.Found,
        headers = List(Location("http://google.com"))
      )

    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          "OOPS, No Man's Land"
        )
      )
  }

  val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandleSync(syncHandler, "localhost", 8388)

  // Shutdown the server
  import system.dispatcher
  bindingFuture
    .flatMap(binding => binding.unbind())
    .onComplete(_ => system.terminate())
  // `system.terminate()` => Shutting down actor System



}
