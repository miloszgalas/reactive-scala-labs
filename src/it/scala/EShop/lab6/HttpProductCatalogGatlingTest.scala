package EShop.lab6

import io.gatling.core.Predef.{Simulation, scenario, _}
import io.gatling.http.Predef.http

import scala.concurrent.duration._

class HttpProductCatalogGatlingTest extends Simulation {
  val feeder = csv(fileName = "data/product_data.csv", quoteChar = ',').random

  val httpProtocol = http
    .baseUrls("http://localhost:9000")
    .acceptHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val scn = scenario("BasicSimulation")
    .feed(feeder)
    .exec(
      http("request")
        .get("/products?brand=${brand}&keywords=${word}")
        .asJson
    )
    .pause(5)

  setUp(
    scn.inject(
      incrementUsersPerSec(10)
        .times(30)
        .eachLevelLasting(5.seconds)
        .separatedByRampsLasting(1.seconds)
        .startingFrom(100)
    )
  ).protocols(httpProtocol)
}
