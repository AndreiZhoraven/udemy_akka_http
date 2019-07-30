package part3_high_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCode, StatusCodes}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives.{complete, _}

import scala.concurrent.duration._
import spray.json._

import scala.util.{Failure, Success}


case class Person(pin: Int, name: String)

trait CitizenRegistrationJSONProtocol extends DefaultJsonProtocol {
  implicit val personJSON = jsonFormat2(Person)
}

object HighLevelExercise extends App with CitizenRegistrationJSONProtocol {

  implicit val system = ActorSystem("HighLevelExercise")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  /**
    * Exercise :
    *
    *   - GET /api/people : retrieve ALL the people you have registered
    *   - GET /api/people/pin : retrieve the person with that PIN, return a JSON
    *   - GET /api/people?pin=X (same)
    *   - (harder) POST /api/people with a JSON payload denoting a Person, add that Person to your DB
    *     - extract the HTTP request's payload (entity)
    *       - extract the request
    *       - process the entity's data
    */


  var people = List(
    Person(1, "Alice"),
    Person(2, "Bob"),
    Person(3, "Tom")
  )

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)


  val personServerRoute =
    pathPrefix("api" / "people") {
      get {
        (path(IntNumber) | parameter(Symbol("pin").as[Int])) { pin =>
          val person = people.find(_.pin == pin)
          complete(
            toHttpEntity(
              person.toJson.prettyPrint
          ))
        } ~
        pathEndOrSingleSlash {
          complete(
            toHttpEntity(
              people.toJson.prettyPrint
            )
          )
        }
      } ~
      (post & pathEndOrSingleSlash & extractRequest & extractLog) { (request, log) =>
        val entity = request.entity
        val strictEntityFuture = entity.toStrict(3 seconds)
        val personFuture = strictEntityFuture.map(_.data.utf8String.parseJson.convertTo[Person])

        onComplete(personFuture) {
          case Success(person) =>
            log.info(s"New Person ${person}")
            people = people :+ person
          case Failure(ex) =>
            failWith(ex)
        }
//        // "side-effect"
//        personFuture.onComplete {
//          case Success(person) =>
//            log.info(s"New Person ${person}")
//            people = people :+ person
//          case Failure(ex) =>
//            log.warning(s"Something failed with fetching the person from the entity: $ex")
//        }
//        complete(personFuture
//          .map(_ => StatusCodes.OK)
//          .recover {
//            case _ => StatusCodes.InternalServerError
//          })
      }
    }

  Http().bindAndHandle(personServerRoute, "localhost", 8080)

}
