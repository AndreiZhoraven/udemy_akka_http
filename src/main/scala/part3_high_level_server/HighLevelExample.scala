package part3_high_level_server

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout

import scala.concurrent.duration._
import part2_low_level_server.{Guitar, GuitarDB, GuitarStoreJSONProtocol}

import scala.concurrent.Future

// step #1
import spray.json._


object HighLevelExample extends App with GuitarStoreJSONProtocol {

  implicit val system = ActorSystem("HighLevelExample")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  import GuitarDB._

  /*
   GET /api/guitar fetches ALL the guitars in the store
   GET /api/guitar?id=X fetches the guitar with id X
   GET /api/guitar/X fetches guitar with id X
   GET /api/guitar/inventory?inStock=true
   */

  /*
    setup
   */
  val guitarDB = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )
  guitarList.foreach(guitar => guitarDB ! CreateGuitar(guitar))

  implicit val timeout = Timeout(2 seconds)
  val guitarServerRoute =
    path("api" / "guitar") {
      // ALWAYS PUT THE MORE SPECIFIC ROUTE FIRST
      parameter(Symbol("id").as[Int]) { guitarId =>
        get {
          val guitarFuture : Future[Option[Guitar]] = (guitarDB ? FindGuitarById(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitarOption =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOption.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      get {
        val guitarsFuture : Future[List[Guitar]] = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
        val entityFuture = guitarsFuture.map { guitars =>
           HttpEntity(
             ContentTypes.`application/json`,
             guitars.toJson.prettyPrint
           )
        }
        complete(entityFuture)
      }
    } ~
    path("api" / "guitar" / IntNumber) { guitarId : Int =>
      get {
        val guitarFuture : Future[Option[Guitar]] = (guitarDB ? FindGuitarById(guitarId)).mapTo[Option[Guitar]]
        val entityFuture = guitarFuture.map { guitarOption =>
          HttpEntity(
            ContentTypes.`application/json`,
            guitarOption.toJson.prettyPrint
          )
        }
        complete(entityFuture)
      }
    } ~
    path("api" / "guitar" / "inventory") {
      get {
        parameter(Symbol("inStock").as[Boolean]) { inStock =>
          val guitarsFuture : Future[List[Guitar]] = (guitarDB ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
          val entityFuture = guitarsFuture.map { guitars =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      }
    }

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val simplifiedGuitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        parameter(Symbol("inStock").as[Boolean]) { inStock =>
          complete(
            (guitarDB ? FindGuitarsInStock(inStock))
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
      (path(IntNumber) | parameter(Symbol("id").as[Int])) { guitarId =>
        complete(
          (guitarDB ? FindGuitarById(guitarId))
            .mapTo[Option[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)
        )
      } ~
      pathEndOrSingleSlash {
        complete(
          (guitarDB ? FindAllGuitars)
            .mapTo[List[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)
        )
      }
    }

  Http().bindAndHandle(simplifiedGuitarServerRoute, "localhost", 8080)
}
