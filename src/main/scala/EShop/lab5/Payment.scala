package EShop.lab5

import EShop.lab2.TypedCheckout
import EShop.lab3.OrderManager
import EShop.lab3.OrderManager.ConfirmPaymentReceived
import EShop.lab5.PaymentService.{PaymentServerError, PaymentSucceeded}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, RestartSupervisorStrategy, SupervisorStrategy, Terminated}

import scala.concurrent.duration._

object Payment {
  sealed trait Message
  case object DoPayment                                                       extends Message
  case class WrappedPaymentServiceResponse(response: PaymentService.Response) extends Message

  sealed trait Response
  case object PaymentRejected extends Response

  val restartStrategy: RestartSupervisorStrategy =
    SupervisorStrategy.restart.withLimit(maxNrOfRetries = 3, withinTimeRange = 1.second)

  def apply(
    method: String,
    orderManager: ActorRef[OrderManager.Command],
    checkout: ActorRef[TypedCheckout.Command]
  ): Behavior[Message] =
    Behaviors
      .receive[Message](
        (context, msg) =>
          msg match {
            case DoPayment =>
              val adapter = context.messageAdapter[PaymentService.Response] {
                case response @ PaymentService.PaymentSucceeded => WrappedPaymentServiceResponse(response)
              }
              val paymentService = Behaviors
                .supervise(PaymentService(method, adapter))
                .onFailure[PaymentServerError](restartStrategy)
              val paymentServiceRef = context.spawnAnonymous(paymentService)
              context.watch(paymentServiceRef)
              Behaviors.same

            case WrappedPaymentServiceResponse(PaymentSucceeded) =>
              orderManager ! ConfirmPaymentReceived
              checkout ! TypedCheckout.ConfirmPaymentReceived
              Behaviors.same
        }
      )
      .receiveSignal {
        case (_, Terminated(_)) =>
          notifyAboutRejection(orderManager, checkout)
          Behaviors.same
      }

  private def notifyAboutRejection(
    orderManager: ActorRef[OrderManager.Command],
    checkout: ActorRef[TypedCheckout.Command]
  ): Unit = {
    orderManager ! OrderManager.PaymentRejected
    checkout ! TypedCheckout.PaymentRejected
  }

}
