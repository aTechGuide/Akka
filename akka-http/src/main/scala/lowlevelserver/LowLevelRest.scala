package lowlevelserver

import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.javadsl.model.HttpEntity.Strict
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Akka HTTP Lecture 8, 9, 10 [Marshalling JSON, Handling Query Parameters]
  *
  * Commands
  * - HTTP POST localhost:8080/api/guitar < src/main/json/guitar.json
  * - HTTP GET localhost:8080/api/guitar
  * - HTTP POST "localhost:8080/api/guitar/inventory?id=2&quantity=4"
  * - HTTP GET "localhost:8080/api/guitar/inventory?inStock=true"
  *
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-http/learn/lecture/14128305#overview
  */

case class Guitar(make: String, model: String, quantity: Int = 0)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars
  case class AddQuantity(id: Int, quantity: Int)
  case class FindGuitarsInStock(inStock: Boolean)
}

class GuitarDB extends Actor with ActorLogging {
  import GuitarDB._

  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("Searching for all guitars")
      sender() ! guitars.values.toList

    case FindGuitar(id) =>
      log.info(s"Searching guitar by id: $id")
      sender() ! guitars.get(id)

    case CreateGuitar(guitar) =>
      log.info(s"Adding Guitar $guitar with id $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)

      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1

    case AddQuantity(id, quantity) =>
      log.info(s"Trying to add $quantity items for guitar $id")
      val guitar: Option[Guitar] = guitars.get(id)
      val newGuitar: Option[Guitar] = guitar.map {
        case Guitar(make, model, q) => Guitar(make, model, q + quantity)
      }

      newGuitar.foreach{ guitar =>
        guitars = guitars + (id -> guitar)
      }

      sender() ! newGuitar

    case FindGuitarsInStock(inStock) =>
      log.info(s"Searching for all guitars ${if(inStock) "in" else "out of"} stock")
      if (inStock)
        sender() ! guitars.values.filter(_.quantity > 0)
      else
        sender() ! guitars.values.filter(_.quantity == 0)
  }
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {

  // `jsonFormat2` is necessary for converting guitars into JSON
  // We are using `jsonFormat2` because the guitar constructor takes 2 arguments
  implicit val guitarFormat: RootJsonFormat[Guitar] = jsonFormat3(Guitar)

}

object LowLevelRest extends App with GuitarStoreJsonProtocol {

  implicit val system: ActorSystem = ActorSystem("LowLevelRest")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher
  import GuitarDB._

  /*
    GET   /api/guitar         => All the guitars in the store
    GET   /api/guitar?id=X    => Fetches the guitar with id X
    POST  /api/guitar         => Insert the guitar into the store

    GET   / api/guitar/inventory?inStock=true/false               => Which returns guitars in stock as a JSON
    POST  / api/guitar/inventory?id=X&quantity=Y                  => Which adds Y guitars to the stock for the guitar with id X


    Marshalling
    - It is the process of Serializing our data to a wire format that an HTTP client can understand.
   */

  // JSON -> Marshalling
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  // println(simpleGuitar.toJson.prettyPrint)

  // Unmarshalling
  val guitarJson =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster",
      |  "quantity": 3
      |
      |}
    """.stripMargin

  // println(guitarJson.parseJson.convertTo[Guitar])

  val guitarDb = system.actorOf(Props[GuitarDB], "guitarDb")
  val guitarList = List(Guitar("Fender", "Stratocaster"), Guitar("Gibbson", "Less Paul"), Guitar("Martin", "LX1"))

  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }

  // Server Code
  // We are going to use an asynchronous handler that is a function from an HTTP request to a Future of HTTP responses.
  // That is because we're interacting with an actor acting as a database.
  // So whenever we need to interact with an external resource use futures because otherwise the response times will be really really bad.

  implicit val defaultTimeout: Timeout = Timeout(2 seconds)

  def getGuitar(query: Query): Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt)

    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id: Int) =>
        val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) => HttpResponse(entity = HttpEntity(
            ContentTypes.`application/json`,
            guitar.toJson.prettyPrint
          ))
        }

    }

  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {

    case HttpRequest(HttpMethods.POST, uri @ Uri.Path("/api/guitar/inventory"), _, _, _) =>

      val guitarId: Option[Int] = uri.query().get("id").map(_.toInt)
      val guitarQuantity: Option[Int] = uri.query().get("quantity").map(_.toInt)

      val validGuitarResponseFuture: Option[Future[HttpResponse]] = for {
        id <- guitarId
        quantity <- guitarQuantity
      } yield {
        val newGuitarFuture: Future[Option[Guitar]] = (guitarDb ? AddQuantity(id, quantity)).mapTo[Option[Guitar]]
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))

      }

      validGuitarResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))

    case HttpRequest(HttpMethods.GET, uri @ Uri.Path("/api/guitar/inventory"), _, _, _) =>

      val inStockOption = uri.query().get("inStock").map(_.toBoolean)

      inStockOption match {
        case Some(inStock) =>
          val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
          guitarsFuture.map { guitars =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            )
          }

        case None => Future(HttpResponse(StatusCodes.BadRequest))

      }

    case HttpRequest(HttpMethods.GET, uri @ Uri.Path("/api/guitar"), _, _, _) =>

      val query = uri.query()

      if (query.isEmpty) {
        val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
        guitarsFuture.map { guitars =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
        }
      } else {
        // fetch guitar associated to guitar Id
        getGuitar(query)
      }



    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      // `entity` is a streams based data structure model as a source of byte strings i.e. Source[ByteString]

      // Akka HTTP will attempt to bring all the contents of this entity into memory from the HTTP connection during the course of three seconds
      val strictEntityFuture: Future[Strict] = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>

        val guitarJsonString = strictEntity.getData.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]

        val guitarCreatedFuture: Future[GuitarCreated] = (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }

      }

    // This case is also very important because if we don't reply to an existing request that will be interpreted as back pressure
    // That back pressure is interpreted by the streams based Akka HTTP server and that will be propagated all the way down to the TCP layer.
    // So all subsequent HTTP requests will become slower and slower and we don't want that.
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)

}
