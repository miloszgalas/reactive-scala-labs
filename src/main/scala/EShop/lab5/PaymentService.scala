package EShop.lab5

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object PaymentService {

  sealed trait Response
  case object PaymentSucceeded extends Response

  case class PaymentClientError() extends Exception
  case class PaymentServerError() extends Exception

  // actor behavior which needs to be supervised
  // use akka.http.scaladsl.Http to make http based payment request
  // use getUri method to obtain url
  def apply(
    method: String,
    payment: ActorRef[Response]
  ): Behavior[HttpResponse] =
    Behaviors.setup { context =>
      implicit val executionContext = context.executionContext
      val http                      = Http(context.system)
      val result                    = http.singleRequest(HttpRequest(uri = getURI(method)))
      context.pipeToSelf(result) {
        case Success(value) => value
        case Failure(e)     => throw e
      }
      Behaviors.receiveMessage {
        case HttpResponse(code, _, _, _) =>
          code match {
            case it if it.isSuccess() =>
              payment ! PaymentSucceeded
              Behaviors.stopped
            case it if it.intValue() >= 400 && it.intValue() < 405 => throw PaymentClientError()
            case it if it.intValue() >= 405                        => throw PaymentServerError()
          }
      }
    }

  // remember running PymentServiceServer() before trying payu based payments
  private def getURI(method: String) =
    method match {
      case "payu"   => "http://127.0.0.1:8080"
      case "paypal" => s"http://httpbin.org/status/408"
      case "visa"   => s"http://httpbin.org/status/200"
      case _        => s"http://httpbin.org/status/404"
    }
}
