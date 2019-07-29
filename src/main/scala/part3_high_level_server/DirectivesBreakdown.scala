package part3_high_level_server

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.stream.ActorMaterializer

object DirectivesBreakdown extends App {

  implicit val system = ActorSystem("DirectivesBreakdown")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  /**
    * Type #1 : filtering directives
    */
  val simpleHttpMethodRoute =
    post { // equivalent directives for get, put, patch, delete, head, options
      complete(StatusCodes.Forbidden)
    }

  val simplePathRoute =
    path("about") {
      complete(HttpEntity(
        ContentTypes.`application/json`,
        """
          |<html>
          | <body>
          |   Hello from the about page !
          | </body>
          |</html>
          |""".stripMargin
      ))
    }

  val complexPathRoute =
    path("api" / "myEndPoint") { // /api/myEndPoint
      complete(StatusCodes.OK)
    }

  val dontConfuse =
    path("api/myEndPoint") {
      complete(StatusCodes.OK)
    }

  val pathEndRoute =
    pathEndOrSingleSlash { // localhost:8080 OR localhost:8080/
      complete(StatusCodes.OK)
    }

  //  Http().bindAndHandle(dontConfuse, "localhost", 8080)

  /**
    * Type #2 : extraction directives
    */
  // GET on /api/item/42
  val pathExtractionRoute =
    path("api" / "item" / IntNumber) { (itemNumber : Int) =>
      // other directives

      println(s"I've got a number in my path: $itemNumber ")
      complete(StatusCodes.OK)
    }

  val pathMultiExtractionRoute =
    path("api" / "order" / IntNumber / IntNumber) { (id, inventory) =>
      println(s"I've got 2 number in my path: $id and $inventory")
      complete(StatusCodes.OK)
    }

  //  Http().bindAndHandle(pathExtractionRoute, "localhost", 8080)

  val queryParamExtractionRoute =
    // /api/item?id=45
  path("api" / "item") {
    parameter(Symbol("id").as[Int] ) { itemId : Int =>
      println(s"I've extracted the ID as $itemId")
      complete(StatusCodes.OK)
    }
  }

  val extractRequestPath =
    path("controlEndPoint") {
      extractRequest { httpRequest : HttpRequest =>
        extractLog { log : LoggingAdapter =>
          log.info(s"I got the http request : $httpRequest")
          complete(StatusCodes.OK)
        }
      }
    }

  /**
    * Type #3 : composite directives
    */
  val pathNestedRoute =
    path("api" / "item") {
      get{
        complete(StatusCodes.OK)
      }
    }

  val compactSimpleNestedRoute = (path("api" / "item") & get) {
    complete(StatusCodes.OK)
  }

  val compactExtractRequestRoute =
    (path("controlEndPoint") & extractRequest & extractLog) { (request, log) =>
      log.info(s"I got the http request : $request")
      complete(StatusCodes.OK)
    }

  // /about and /aboutUs
  val repeatedRoute =
    path("about") {
      complete(StatusCodes.OK)
    } ~
    path("aboutUs") {
      complete(StatusCodes.OK)
    }

  val dryRoute =
    (path("about") | path("aboutUs")) {
      complete(StatusCodes.OK)
    }

  // yourblog.com/42 AND yourblog.com?postId=42
  val blogByIdRoute =
    path(IntNumber) { blogPostId : Int =>
      // complex server logic
      complete(StatusCodes.OK)
    }

  val blogByQueryParamRoute =
    parameter(Symbol("postId").as[Int]) { blogPostId: Int =>
      // the SAME server logic
      complete(StatusCodes.OK)
    }

  val combinedBlogByIdRoute =
    (path(IntNumber) | parameter(Symbol("postId").as[Int])) { blogPostId: Int =>
      // your original server logic
      complete(StatusCodes.OK)
    }

  /**
    * Type #4 : "actionnable" directives
    */
  val completeOkRoute = complete(StatusCodes.OK)

  val failedRoute =
    path("notSupported") {
      failWith(new RuntimeException("Unsupported!")) // complete with HTTP 500 Internal Server Error
    }

  val routeWithRejection =
    path("home") {
      reject
    } ~
    path("index") {
      completeOkRoute
    }

  /**
    * Exercise
    */
  val getOrPutPath =
    path("api" / "myEndPoint") {
      get {
        completeOkRoute
      }
      post {
        complete(StatusCodes.Forbidden)
      } 
    }

  Http().bindAndHandle(getOrPutPath, "localhost", 8081)
}
