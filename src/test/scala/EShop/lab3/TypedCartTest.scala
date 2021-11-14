package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.Effect.{MessageAdapter, Scheduled, Spawned, TimerCancelled, TimerScheduled}
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val cart  = testKit.spawn(TypedCartActor(), "cart")
    val probe = testKit.createTestProbe[Cart]()

    cart ! TypedCartActor.AddItem("item")
    cart ! TypedCartActor.GetItems(probe.ref)
    probe.expectMessage(Cart(Seq("item")))
  }

  it should "be empty after adding and removing the same item" in {
    val testKit = BehaviorTestKit(TypedCartActor())
    val inbox   = TestInbox[Cart]()

    testKit.run(TypedCartActor.AddItem("item"))
    testKit.run(TypedCartActor.RemoveItem("item"))
    testKit.run(TypedCartActor.GetItems(inbox.ref))
    inbox.expectMessage(Cart.empty)
  }

  it should "start checkout" in {
    val testKit                = BehaviorTestKit(TypedCartActor())
    val managerCartMapperInbox = TestInbox[TypedCartActor.Event]()

    testKit.run(TypedCartActor.AddItem("item"))
    testKit.run(TypedCartActor.StartCheckout(managerCartMapperInbox.ref))
    testKit.expectEffectType[Scheduled[TypedCartActor.Command]]
    testKit.expectEffectType[Spawned[TypedCheckout.Command]]
  }
}
