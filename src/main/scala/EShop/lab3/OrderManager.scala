package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  case object ConfirmPaymentReceived                                                                  extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager {

  import OrderManager._

  var cartMapper: ActorRef[TypedCartActor.Event]    = _
  var checkoutMapper: ActorRef[TypedCheckout.Event] = _
  var paymentMapper: ActorRef[Payment.Event]        = _

  def start: Behavior[OrderManager.Command] =
    Behaviors.setup { context =>
      cartMapper = context.messageAdapter {
        case TypedCartActor.CheckoutStarted(checkoutRef) => ConfirmCheckoutStarted(checkoutRef)
      }

      checkoutMapper = context.messageAdapter {
        case TypedCheckout.PaymentStarted(paymentRef, _) => ConfirmPaymentStarted(paymentRef)
      }

      paymentMapper = context.messageAdapter {
        case Payment.PaymentReceived => ConfirmPaymentReceived
      }
      uninitialized
    }

  def uninitialized: Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case AddItem(id, sender) =>
          val cartActor = context.spawn(new TypedCartActor().start, "TypedCart")
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          open(cartActor)
      }
    )

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case AddItem(id, sender) =>
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          Behaviors.same
        case RemoveItem(id, sender) =>
          cartActor ! TypedCartActor.RemoveItem(id)
          Behaviors.same
        case Buy(sender) =>
          cartActor ! TypedCartActor.StartCheckout(cartMapper)
          sender ! Done
          inCheckout(cartActor, sender)
      }
    )

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case ConfirmCheckoutStarted(checkoutRef) =>
          inCheckout(checkoutRef)
      }
    )

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, paymentMapper, checkoutMapper)
          sender ! Done
          inPayment(sender)
      }
    )

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case ConfirmPaymentStarted(paymentRef) =>
          inPayment(paymentRef, senderRef)
        case ConfirmPaymentReceived =>
          finished
      }
    )

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case Pay(sender) =>
          paymentActorRef ! Payment.DoPayment
          sender ! Done
          inPayment(senderRef)
      }
    )

  def finished: Behavior[OrderManager.Command] = Behaviors.stopped
}
