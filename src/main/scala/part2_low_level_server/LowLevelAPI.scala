package part2_low_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._

object LowLevelAPI extends App {

  implicit val system = ActorSystem("LowLevelAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val serverSource = Http().bind("localhost", 8000)
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connec tion from: ${connection.remoteAddress}")
  }

  val serverBindingFuture = serverSource.to(connectionSink).run()
  serverBindingFuture.onComplete {
    case Success(binding) =>
      println("Server binding successful")
      binding.terminate(2 seconds)
    case Failure(ex) => println(s"Server binding failed: $ex")
  }

  /*
    Method #1 : synchronously serve HTTP responses
   */

  val requestHandler : HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, uri, value, entity, protocol) =>
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP!
            | </body>
            |</html>
            |""".stripMargin
        )
      )

    case request : HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   OOPS! The resources can't be found.
            | </body>
            |</html>
            |""".stripMargin
        )
      )
  }

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

  //  Http().bind("localhost", 8080).runWith(httpSyncConnectionHandler)
  // shorthand version :
  //  Http().bindAndHandleSync(requestHandler, "localhost", 8080)

  /*
    Method #2: serve back HttpResponse ASYNCHRONOUSLY
   */

  val asyncRequestHandler : HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), value, entity, protocol) =>
      Future(HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP!
            | </body>
            |</html>
            |""".stripMargin
        )
      ))

    case request : HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   OOPS! The resources can't be found.
            | </body>
            |</html>
            |""".stripMargin
        )
      ))
  }

  val httpAsyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }

  // async-based "manual" version
//  Http().bind("localhost", 8081).runWith(httpAsyncConnectionHandler)
  // shorthand version
  Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8081)


  /*
    Method #3: async via Akka streams
   */

  val streamsBasedRequestHandler : Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), value, entity, protocol) =>
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP!
            | </body>
            |</html>
            |""".stripMargin
        )
      )

    case request : HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   OOPS! The resources can't be found.
            | </body>
            |</html>
            |""".stripMargin
        )
      )
  }

  // streams-based "manual" version
  // "manual" version
  //  Http().bind("localhost", 8082).runForeach { connection =>
  //    connection.handleWith(streamsBasedRequestHandler)
  //  }

  // shorthand version
  Http().bindAndHandle(streamsBasedRequestHandler, "localhost", 8082)


  /**
    * Exercise : create your own HTTP server running on localhost:8388, which replies
    *   - with a welcome message on the "front door" => localhost:8388/
    *   - with a proper HTML on localhost:8388/about
    *   - with a 404 message otherwise
    */

  val syncExerciseHandler : HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), value, entity, protocol) =>
      HttpResponse(
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          "Hello from the front door"
        )
      )
    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), value, entity, protocol) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   This is about page
            | </body>
            |</html>
            |""".stripMargin
        )
      )

      // path /search redirects to some other part of our website/webapp/microservices
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), value, entity, protocol) =>
      HttpResponse(
        StatusCodes.Found,
        headers = List(Location("http://google.com"))
      )



    case request : HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          "OOPS! The resources can't be found."
        )
      )
  }


  //  Http().bind("localhost", 8388).runWith(httpSyncConnectionHandler)
  // shorthand version :
  val bindingFuture = Http().bindAndHandleSync(syncExerciseHandler, "localhost", 8388)

  // shutdown the server:
  bindingFuture
    .flatMap(binding => binding.unbind())
    .onComplete(_ => system.terminate())
}
