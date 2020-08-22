import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import scala.io.StdIn

object HelloWorld extends App {

  implicit val system: ActorSystem = ActorSystem("HelloWorld")
  implicit val materializer: ActorMaterializer = ActorMaterializer()


  val simpleRoute =
    pathEndOrSingleSlash {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          | <body>
          |   Hello World
          |   </br>
          |   From Rock the JVM with Akka HTTP!
          | </body>
          |</html>
        """.stripMargin
      ))
    }

  val bindingFuture = Http().bindAndHandle(simpleRoute, "localhost", 8080)

  // wait for a new line, then terminate the server
  import system.dispatcher
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())

  val l = List(2).flatMap(Seq(_))
}
