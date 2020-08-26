package highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import lowlevelserver.HTTPsContext

/**
  * Akka HTTP Lecture 12 [High Level Server API Intro]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-http/learn/lecture/14128317#overview
  */

object HighLevelIntro extends App {

  implicit val system: ActorSystem = ActorSystem("LowLevelRest")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  // directives
  import akka.http.scaladsl.server.Directives._

  val simpleRoute: Route =
    path("home") { // `path("home")` is called a DIRECTIVE
      complete(StatusCodes.OK) // `complete()` is also called a DIRECTIVE
    }

  // Directives are building blocks of high level Akka HTTP server logic.
  // So we will build our server logic based on these directives
  // directives specify what happens under which conditions.

  // For example this `path(home)` directive will filter only the HTTP requests that are trying to hit the  /home path.
  // That's why this directive is called Path.

  // If the HTTP request passes this filter then `complete()` directive decides what will happen.
  // So `complete()` directive will send back an HTTP response with the status code OK

  // The result of applying directives and composing directives is called a ROUTE

  // Above route says is that if an HTTP request tries to hit /home it will complete the response with status code. OK.
  // Everything else will be completed by a 404.

  val pathGetRoute: Route =
    path("home") {
      get {
        complete(StatusCodes.OK)
      }
    }

  // Chaining Directives
  val chainedRoute: Route =
    path("myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~ // Means "otherwise"
      post {
        complete(StatusCodes.Forbidden)
      }
    } ~
    path("home") {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            | Hello from High Level Akka HTTP
            | </body>
            |</html>
          """.stripMargin
        )
      )
    } // Routing Tree

  Http().bindAndHandle(pathGetRoute, "localhost", 8080)
  // The `route` is implicitly converted to a Flow so that the `bindAndHandle` method can take it.

  // For HTTPs
  Http().bindAndHandle(pathGetRoute, "localhost", 8443, HTTPsContext.httpsConnectionContext)

}
