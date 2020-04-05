package com.lightbend.training.coffeehouse

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.actor.SupervisorStrategy._
import akka.routing.FromConfig

import scala.concurrent.duration._

class CoffeeHouse(caffeineLimit: Int) extends Actor with ActorLogging {
  import CoffeeHouse._

  private val baristaAccuracy: Int =
    context.system.settings.config.getInt("coffee-house.barista.accuracy")
  private val waiterMaxComplaintCount: Int =
    context.system.settings.config.getInt("coffee-house.waiter.max-complaint")
  private val finiteDuration: FiniteDuration =
    context.system.settings.config.getDuration("coffee-house.guest.duration", TimeUnit.MILLISECONDS).milli
  private val prepareCoffeeDuration: FiniteDuration =
    context.system.settings.config.getDuration(("coffee-house.barista.prepare-coffee-duration"), TimeUnit.MILLISECONDS).millis

  private val barista = createBarista()
  private val waiter = createWaiter()
  protected def createGuest(favoriteCoffee: Coffee, caffeineLimit: Int): ActorRef =
    context.actorOf(Guest.props(waiter, favoriteCoffee, finiteDuration, caffeineLimit))
  protected def createWaiter(): ActorRef =
    context.actorOf(Waiter.props(self, barista, waiterMaxComplaintCount), "waiter")
  protected def createBarista(): ActorRef =
    context.actorOf(
      FromConfig.props(Barista.props(prepareCoffeeDuration, baristaAccuracy)), "barista")

  private var guestBook: Map[ActorRef, Int] = Map.empty.withDefaultValue(0)

  // Supervisor strategy
  override val supervisorStrategy: SupervisorStrategy = {

    val decider: SupervisorStrategy.Decider = {
      case Guest.CaffeineException => Stop
      case Waiter.FrustratedException(coffee, guest) =>
        barista.forward(Barista.PrepareCoffee(coffee, guest))
        Restart
    }
    OneForOneStrategy()(decider.orElse(super.supervisorStrategy.decider))
  }

  log.debug("CoffeeHouse Open")
  override def receive: Receive = {
    case CreateGuest(favoriteCoffee, caffeineLimit) =>
      val guest = createGuest(favoriteCoffee, caffeineLimit)
      guestBook += (guest -> 0)
      context.watch(guest)
      log.info(s"Guest ${guest} added to guest book" )

    case ApproveCoffee(favoriteCoffee, guest) if (guestBook(guest) < caffeineLimit) =>
      guestBook += guest -> (guestBook(guest) + 1)
      log.info(s"Guest ${guest} caffeine count incremented.")
      barista.forward(Barista.PrepareCoffee(favoriteCoffee, guest))
    case ApproveCoffee(_, guest) if (guestBook(guest) >= caffeineLimit) =>
      log.info(s"Sorry, ${guest}, but you have reached your limit.")
      context.stop(guest)
    case GetStatus => sender() ! Status(guestBook.size)
    case Terminated(guest) =>
      guestBook = guestBook.removed(guest)
      log.info(s"Thanks ${guest}, for being our guest!")
  }
}

object CoffeeHouse {
  case class CreateGuest(favoriteCoffee: Coffee, caffeineLimit: Int)
  case class ApproveCoffee(coffee: Coffee, guest: ActorRef)
  case object GetStatus
  case class Status(guestCount: Int)
  def props(caffeineLimit: Int): Props = Props(new CoffeeHouse(caffeineLimit))
}
