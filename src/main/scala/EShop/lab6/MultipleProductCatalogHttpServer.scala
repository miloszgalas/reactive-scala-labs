package EShop.lab6

import EShop.lab5.{ProductCatalog, SearchService}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.{schedulerFromActorSystem, Askable}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}
import scala.util.Try

//json formats
trait ProductCatalogJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }

  implicit val itemFormat  = jsonFormat5(ProductCatalog.Item)
  implicit val itemsFormat = jsonFormat1(ProductCatalog.Items)
}

class MultipleProductCatalogHttpServer extends ProductCatalogJsonSupport {
  implicit val system           = ActorSystem(Behaviors.empty, "ReactiveRouters")
  implicit val executionContext = system.executionContext
  val workers                   = system.systemActorOf(Routers.pool(5)(ProductCatalog(new SearchService())), "workersRouter")

  implicit val timeout: Timeout = 5.seconds

  def routes: Route = {
    path("products") {
      get {
        parameter(Symbol("brand").as[String], Symbol("keywords").as[String]) { (brand, keywords) =>
          complete {
            val items =
              workers
                .ask(ref => ProductCatalog.GetItems(brand, keywords.split(" ").toList, ref))
                .mapTo[ProductCatalog.Items]

            Future.successful(items)
          }
        }
      }
    }
  }

  def run(port: Int): Unit = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.ready(bindingFuture, Duration.Inf)
  }
}

object MultipleProductCatalogApp extends App {
  val multipleProductCatalogHttpServer = new MultipleProductCatalogHttpServer()
  multipleProductCatalogHttpServer.run(Try(args(0).toInt).getOrElse(9000))
}
