package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {
  sealed trait Command
  case object DoPayment extends Command

  sealed trait Event
  case object PaymentReceived extends Event

  def apply(
    method: String,
    orderManager: ActorRef[Event],
    checkout: ActorRef[TypedCheckout.Command]
  ): Behavior[Command] =
    Behaviors.setup(context => new Payment(method, orderManager, checkout).start)
}

class Payment(
  method: String,
  orderManager: ActorRef[Payment.Event],
  checkout: ActorRef[TypedCheckout.Command]
) {

  import Payment._

  def start: Behavior[Payment.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case DoPayment =>
          orderManager ! PaymentReceived
          checkout ! TypedCheckout.ConfirmPaymentReceived
          Behaviors.stopped
      }
    )

}
