package part2_low_level_server

import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

// step #1
import spray.json._

case class Guitar(make: String, model: String, quantity: Int = 0)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitarById(id: Int)
  case object FindAllGuitars
  case class UpdateGuitarQuantity(guitarId: Int, quantity: Int)
  case class FindGuitarsInStock(inStock: Boolean)
}

class GuitarDB extends Actor with ActorLogging {
  import GuitarDB._

  var guitars : Map[Int, Guitar] = Map()
  var currentGuitarId : Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("Searching for all guitars")
      sender() ! guitars.values.toList

    case FindGuitarById(id) =>
      log.info(s"Searching guitar by id $id")
      sender() ! guitars.get(id)

    case CreateGuitar(guitar) =>
      log.info(s"Adding guitar $guitar with id $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1

    case UpdateGuitarQuantity(id, newQuantity) =>
      log.info(s"Trying to add $newQuantity guitar of guitar with id $id")
      val guitarOption = guitars.get(id)
      val newGuitar = guitarOption.map {
        case Guitar(make, model, quantity) => Guitar(make, model, quantity + newQuantity)
      }
      newGuitar.foreach(guitar => guitars = guitars + (id -> guitar))
      sender() ! newGuitar

    case FindGuitarsInStock(inStock) =>
      log.info(s"Searching for all guitars ${if(inStock) "in" else "out of"} stock")
      if (inStock)
        sender() ! guitars.values.filter(_.quantity > 0).toList
      else
        sender() ! guitars.values.filter(_.quantity == 0).toList
  }
}

// step #2
trait GuitarStoreJSONProtocol extends DefaultJsonProtocol {
  // step #3
  implicit val guitarFormat = jsonFormat3(Guitar)
}

object LowLevelRest extends App with GuitarStoreJSONProtocol {

  implicit val system = ActorSystem("LowLevelRest")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import part2_low_level_server.GuitarDB._

  /*
    - GET on localhost:8080/api/guitar => ALL the guitars in the store
    - GET on localhost:8080/api/guitar?id=X => fetches the guitar associated with id X
    - POST on localhost:8080/api/guitar => insert the guitar into the store
   */

  // JSON -> marshalling
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson.prettyPrint)

  // unmarshalling
  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster",
      |  "quantity": 1
      |}
      |""".stripMargin
  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])

  /*
    set-up
   */
  val guitarDB = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )
  guitarList.foreach(guitar => guitarDB ! CreateGuitar(guitar))

  /*
    server code
   */

  implicit val defaultTimeout = Timeout(2 seconds)

  def getGuitar(query: Query) : Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt) // Option[Int]
    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound)) // /api/guitar?id=
      case Some(id: Int) =>
        val guitarFuture : Future[Option[Guitar]] = (guitarDB ? FindGuitarById(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound) // /api/guitar?id=9000
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
          )
        }
    }
  }



  val requestHandler: HttpRequest => Future[HttpResponse] = {

    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val guitarId : Option[Int] = query.get("id").map(_.toInt)
      val guitarQuantity : Option[Int] = query.get("quantity").map(_.toInt)

      val validGuitarResponseFuture : Option[Future[HttpResponse]]= for {
        id <- guitarId
        quantity <- guitarQuantity
      } yield {
        val newGuitarFuture : Future[Option[Guitar]] = (guitarDB ? UpdateGuitarQuantity(id, quantity)).mapTo[Option[Guitar]]
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
      }
      validGuitarResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))


    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      // fetch the guitar associated with the boolean inStock
      val query = uri.query()
      val inStockOption = query.get("inStock").map(_.toBoolean) // Option[Boolean]
      inStockOption match {
        case Some(inStock) =>
          val guitarsFuture : Future[List[Guitar]] = (guitarDB ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
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


    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"), value, entity, protocol) =>
      /*
        query parameter handling code
       */

      val query = uri.query() // query object <=> Map[String, String]
      if(query.isEmpty) {
        val guitarsFuture : Future[List[Guitar]] = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
        guitarsFuture.map { guitars =>
          HttpResponse (
            entity = HttpEntity (
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
        }
      }
      else {
        // fetch guitar associated to the guitar id
        // localhost:8080/api/guitar?id=45
        getGuitar(query)
      }

    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      // entity are a Source[ByteString]
      val strictEntityFuture = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>

        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]

        val guitarCreatedFuture : Future[GuitarCreated] = (guitarDB ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }
      }

    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)

  /**
    * Exercise : enhance the Guitar case class with a quantity field, by default: 0
    *   - GET to /api/guitar/inventory?inStock=true/false which returns the guitars instock as a JSON
    *   - POST to /api/guitar/inventory?id=X&quantity=Y which adds Y guitars to the stock for guitar with id X
    */



}
