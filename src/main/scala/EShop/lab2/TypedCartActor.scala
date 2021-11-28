package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager

import java.time.Instant

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                              extends Command
  case class RemoveItem(item: Any)                           extends Command
  case object ExpireCart                                     extends Command
  case class StartCheckout(orderManagerRef: ActorRef[Event]) extends Command
  case object ConfirmCheckoutCancelled                       extends Command
  case object ConfirmCheckoutClosed                          extends Command
  case class GetItems(sender: ActorRef[Cart])                extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
  case class ItemAdded(item: Any)           extends Event
  case class ItemRemoved(item: Any)                                        extends Event
  case object CartEmptied                                                  extends Event
  case object CartExpired                                                  extends Event
  case object CheckoutClosed                                               extends Event
  case object CheckoutCancelled                                            extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable]) {
    def cart: Cart
  }
  case object Empty extends State(None) {
    def cart: Cart = Cart.empty
  }
  case class NonEmpty(cart: Cart, timer: Cancellable) extends State(Some(timer))
  case class InCheckout(cart: Cart)                   extends State(None)

  def apply(): Behavior[Command] = Behaviors.setup(context => new TypedCartActor().start)
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case AddItem(item) =>
          nonEmpty(Cart(Seq(item)), scheduleTimer(context))
        case GetItems(sender) =>
          sender ! Cart.empty
          Behaviors.same
      }
    )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case AddItem(item) =>
          cart.addItem(item)
          Behaviors.same
        case RemoveItem(item) =>
          var newCart = cart.removeItem(item)
          if (newCart.size == 0) {
            timer.cancel()
            empty
          } else
            Behaviors.same
        case ExpireCart =>
          timer.cancel()
          empty
        case StartCheckout(orderManagerRef) =>
          timer.cancel()
          val checkout = context.spawn(new TypedCheckout(context.self).start, "Checkout")
          checkout ! TypedCheckout.StartCheckout
          orderManagerRef ! CheckoutStarted(checkout)
          inCheckout(cart)
        case GetItems(sender) =>
          sender ! cart
          Behaviors.same
      }
    )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case ConfirmCheckoutCancelled =>
          nonEmpty(cart, scheduleTimer(context))
        case ConfirmCheckoutClosed =>
          empty
      }
    )

}
