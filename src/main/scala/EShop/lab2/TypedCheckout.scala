package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import scala.language.postfixOps

import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

object TypedCheckout {
  def apply(cartActor: ActorRef[TypedCartActor.Command]): Behavior[Command] =
    Behaviors.setup(context => new TypedCheckout(cartActor).start)

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(
    payment: String,
    managerPaymentMapper: ActorRef[Payment.Event],
    managerCheckoutMapper: ActorRef[Event]
  )                                  extends Command
  case object ExpirePayment          extends Command
  case object ConfirmPaymentReceived extends Command

  sealed trait Event
  case object CheckOutClosed                                       extends Event
  case object CheckOutCancelled                                    extends Event
  case class PaymentStarted(paymentRef: ActorRef[Payment.Command]) extends Event
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case StartCheckout =>
          selectingDelivery(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))
      }
    )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive((_, msg) =>
      msg match {
        case SelectDeliveryMethod(_) =>
          selectingPaymentMethod(timer)
        case CancelCheckout =>
          cancelled
        case ExpireCheckout =>
          cancelled
      }
    )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case SelectPayment(payment, managerPaymentMapper, managerCheckoutMapper) =>
          timer.cancel()
          val paymentRef = context.spawn(Payment(payment, managerPaymentMapper, context.self), "payment")
          managerCheckoutMapper ! PaymentStarted(paymentRef)
          processingPayment(context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment))
        case CancelCheckout =>
          cancelled
        case ExpireCheckout =>
          cancelled
      }
    )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive((_, msg) =>
      msg match {
        case ExpirePayment =>
          cartActor ! TypedCartActor.ConfirmCheckoutCancelled
          cancelled
        case CancelCheckout =>
          cartActor ! TypedCartActor.ConfirmCheckoutCancelled
          cancelled
        case ConfirmPaymentReceived =>
          timer.cancel()
          cartActor ! TypedCartActor.ConfirmCheckoutClosed
          closed
      }
    )

  def cancelled: Behavior[TypedCheckout.Command] =
    Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] =
    Behaviors.stopped

}
