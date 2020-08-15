package persistence.practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

/**
  * Persistence Lecture 18 [Detaching Domain Model from Data Model]
  *
  * Domain Model
  * - Our Events and Commands
  * - Definitions our persistent actors understand
  *
  * Data Model
  * - Definitions of actual persisted Objects in the Journal
  *
  * EventAdapter
  * - Convert Events from domain model to events in the data model
  * - Our schema evolution happens in the event adopters and in the data model
  * - so our actors are unaffected and transparent with regards to schema evolution
  *
  * This technique will bring some amazing benefits to your Akka persistence application architecture.
  * - First of all our business logic and our persistent actors are completely unaware of any schema evolution problems
  * because this schema evolution is done in the adapter only
  * - so it's very clear where you need to actually go about and implement our schema evolution code.
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-persistence/learn/lecture/13002478
  */

object DomainModel {

  // Data Structures
  case class User(id: String, email: String, name: String)
  case class Coupon(code: String, promotionAmount: Int)

  // Command
  case class ApplyCoupon(coupon: Coupon, user: User)

  // Event
  case class CouponApplied(code: String, user: User)
}

object DataModel {

  // Schema Versioning Only Happens here When Schema is Evolved
  case class WrittenCouponApplied(code: String, userId: String, userEmail: String)
  case class WrittenCouponAppliedV2(code: String, userId: String, userEmail: String, username: String)
}

class ModelAdapter extends EventAdapter {
  import DomainModel._
  import DataModel._

  // manifest provides Type Hints
  override def manifest(event: Any): String = "CMAdapter" // Putting Any String here

  // Actor -> toJournal -> serializer -> journal
  override def toJournal(event: Any): Any = event match {
    case event @ CouponApplied(code, user) =>
      println(s"Converting $event to DATA Model")
      WrittenCouponAppliedV2(code, user.id, user.email, user.name)
  }

  // journal -> serializer -> fromJournal -> to the actor
  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case event @ WrittenCouponApplied(code, userId, userEmail) =>
      println(s"Converting $event to DOMAIN Model")
      EventSeq.single(CouponApplied(code, User(userId, userEmail, "")))

    case event @ WrittenCouponAppliedV2(code, userId, userEmail, username) =>
      println(s"Converting $event to DOMAIN Model")
      EventSeq.single(CouponApplied(code, User(userId, userEmail, username)))

    case other => EventSeq.single(other)
  }
}

object DetachingModel extends App {

  import DomainModel._

  class CouponManager extends PersistentActor with ActorLogging {

    val coupons: mutable.Map[String, User] = new mutable.HashMap[String, User]()
    override def receiveRecover: Receive = {
      case event @ CouponApplied(code, user) =>
        log.info(s"Recovered $event")

        coupons.put(code, user)
    }

    override def receiveCommand: Receive = {
      case ApplyCoupon(coupon, user) =>
        if (!coupons.contains(coupon.code)) {
          persist(CouponApplied(coupon.code, user)) { e =>
            coupons.put(coupon.code, user)

            log.info(s"Persisted $e")
          }
        }
    }

    override def persistenceId: String = "coupon-manager"
  }

  val system = ActorSystem("DetachingModel", ConfigFactory.load("application-persistence.conf").getConfig("detachingModels"))
  val couponManager = system.actorOf(Props[CouponManager], "couponManager")


//  for ( i <- 10 to 15) {
//    val coupon = Coupon(s"MEGA_MODEL_$i", 100)
//    val user = User(s"$i", s"user_$i@rtjvm.com", s"Kamran $i")
//
//    couponManager ! ApplyCoupon(coupon, user)
//  }

}
