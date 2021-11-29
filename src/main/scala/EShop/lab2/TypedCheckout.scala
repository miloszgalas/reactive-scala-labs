package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

import java.time.Instant

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
  ) extends Command
  case object ExpirePayment          extends Command
  case object ConfirmPaymentReceived extends Command
  case object PaymentRejected        extends Command

  sealed trait Event
  case object CheckOutClosed                                                        extends Event
  case class PaymentStarted(payment: ActorRef[Payment.Command]) extends Event
  case object CheckoutStarted                                                       extends Event
  case object CheckoutCancelled                                                     extends Event
  case class DeliveryMethodSelected(method: String)             extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(Some(timer))
  case class SelectingPaymentMethod(timer: Cancellable) extends State(Some(timer))
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(Some(timer))
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] =
    Behaviors.receive(
      (context, msg) =>
        msg match {
          case StartCheckout =>
            selectingDelivery(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))
      }
    )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive(
      (_, msg) =>
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
    Behaviors.receive(
      (_, msg) =>
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
          case PaymentRejected =>
            timer.cancel()
            cartActor ! TypedCartActor.ConfirmCheckoutCancelled
            cancelled
      }
    )

  def cancelled: Behavior[TypedCheckout.Command] =
    Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] =
    Behaviors.stopped

}
