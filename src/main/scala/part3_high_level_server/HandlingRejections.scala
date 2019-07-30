package part3_high_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Rejection, RejectionHandler}

object HandlingRejections extends App {

  implicit val system = ActorSystem("HandlingRejections")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleRoute =
    path("api" / "myEndPoint") {
      get {
        complete(StatusCodes.OK)
      } ~
      parameter("id") { _ =>
        complete(StatusCodes.OK)
      }
    }

  // Rejection handlers
  val badRequestHandler: RejectionHandler = { rejection : Seq[Rejection] =>
    println(s"I have encountered rejections: $rejection")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { rejection : Seq[Rejection] =>
    println(s"I have encountered rejections: $rejection")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers =
    handleRejections(badRequestHandler) {
      // define server logic inside
      path("api" / "myEndPoint") {
        get {
          complete(StatusCodes.OK)
        } ~
        post {
          handleRejections(forbiddenHandler) {
            parameter(Symbol("myParam")) { _ =>
              complete(StatusCodes.OK)
            }
          }
        }
      }
    }

  Http().bindAndHandle(simpleRouteWithHandlers, "localhost", 8080)


}
