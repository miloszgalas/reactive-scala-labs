package EShop.lab6

import EShop.lab5.{ProductCatalog, SearchService}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{_symbol2NR, as, complete, entity, get, parameter, parameters, path, post}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Try

object SeedNode extends App {
  private val instancesPerNode = 1
  private val config           = ConfigFactory.load()

  val system = ActorSystem[Nothing](
    Behaviors.setup[Nothing] { ctx =>
      for (i <- 1 to instancesPerNode) yield ctx.spawn(ProductCatalog(new SearchService), s"worker$i")
      Behaviors.same
    },
    "ProductCatalogCluster",
    config
      .getConfig(Try(args(0)).getOrElse("seed-node1"))
      .withFallback(config)
  )
}

class WorkHttpServerInCluster() extends ProductCatalogJsonSupport {
  private val config = ConfigFactory.load()

  implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalogCluster",
    config
  )

  implicit val scheduler        = system.scheduler
  implicit val executionContext = system.executionContext

  // distributed Group Router, workers possibly on different nodes
  val workers = system.systemActorOf(Routers.group(ProductCatalog.ProductCatalogServiceKey), "clusterWorkerRouter")

  implicit val timeout: Timeout = 5.seconds

  def routes: Route =
    path("products") {
      get {
        parameter(Symbol("brand").as[String], Symbol("keywords").as[String]) { (brand, keywords) =>
          complete {
            val items = workers
              .ask(ref => ProductCatalog.GetItems(brand, keywords.split(" ").toList, ref))
              .mapTo[ProductCatalog.Items]

            Future.successful(items)
          }
        }
      }
    }

  def run(port: Int): Unit = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.ready(bindingFuture, Duration.Inf)
  }
}

object WorkHttpClusterApp extends App {
  val workHttpServerInCluster = new WorkHttpServerInCluster()
  workHttpServerInCluster.run(args(0).toInt)
}
