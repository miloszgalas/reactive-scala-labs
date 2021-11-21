package EShop.lab4

import EShop.lab2.TypedCartActor.ConfirmCheckoutClosed
import EShop.lab2.TypedCheckout.CancelCheckout
import EShop.lab2.{TypedCartActor, TypedCheckout}
import EShop.lab3.{OrderManager, Payment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._

class PersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  private def schedule(context: ActorContext[Command], duration: FiniteDuration): Cancellable =
    context.scheduleOnce(duration, context.self, ExpireCheckout)

  def apply(cartActor: ActorRef[TypedCartActor.Command], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context, cartActor),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCartActor.Command]
  ): (State, Command) => Effect[Event, State] =
    (state, command) => {
      state match {
        case WaitingForStart =>
          command match {
            case StartCheckout => Effect.persist(CheckoutStarted(Instant.now()))
          }
        case SelectingDelivery(_) =>
          command match {
            case SelectDeliveryMethod(method)    => Effect.persist(DeliveryMethodSelected(method, Instant.now()))
            case CancelCheckout | ExpireCheckout => Effect.persist(CheckoutCancelled)
          }

        case SelectingPaymentMethod(_) =>
          command match {
            case SelectPayment(payment, managerPaymentMapper, managerCheckoutMapper) =>
              Effect
                .persist(PaymentStarted(null, Instant.now()))
                .thenRun { _ =>
                  val paymentRef =
                    context.spawn(new Payment(payment, managerPaymentMapper, context.self).start, "Payment")
                  managerCheckoutMapper ! PaymentStarted(paymentRef, null)
                }
            case CancelCheckout | ExpireCheckout => Effect.persist(CheckoutCancelled)
          }

        case ProcessingPayment(_) =>
          command match {
            case CancelCheckout | ExpireCheckout => Effect.persist(CheckoutCancelled)
            case ConfirmPaymentReceived =>
              Effect
                .persist(CheckOutClosed)
                .thenRun(_ => cartActor ! ConfirmCheckoutClosed)
          }

        case Cancelled =>
          Effect.stop()

        case Closed =>
          Effect.stop()

      }
    }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State =
    (state, event) => {
      event match {
        case CheckoutStarted(startTime) =>
          SelectingDelivery(schedule(context, timerDuration - ChronoUnit.MILLIS.between(startTime, Instant.now()).milliseconds))
        case DeliveryMethodSelected(_, startTime) =>
          state.timerOpt match {
            case Some(timer) => timer.cancel()
            case None        =>
          }
          SelectingPaymentMethod(schedule(context, timerDuration - ChronoUnit.MILLIS.between(startTime, Instant.now()).milliseconds))
        case PaymentStarted(_, startTime) =>
          state.timerOpt match {
            case Some(timer) => timer.cancel()
            case None        =>
          }
          ProcessingPayment(schedule(context, timerDuration - ChronoUnit.MILLIS.between(startTime, Instant.now()).milliseconds))
        case CheckOutClosed =>
          state.timerOpt match {
            case Some(timer) => timer.cancel()
            case None        =>
          }
          Closed
        case CheckoutCancelled =>
          state.timerOpt match {
            case Some(timer) => timer.cancel()
            case None        =>
          }
          Cancelled
      }
    }
}
