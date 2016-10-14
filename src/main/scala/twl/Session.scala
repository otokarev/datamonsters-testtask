package twl

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.event.LoggingReceive

class Session(players: Seq[ActorRef]) extends Actor with ActorLogging {
  import twl.Session._

  val correctValue = "3"

  players.foreach {a =>
    a ! SessionCreated
    context.watch(a)
  }

  var currentValue = scheduleTick

  def receive = LoggingReceive {
    case t @ Tick(msg) =>
      players.foreach {_ ! t}
      currentValue = scheduleTick
    case t @ Tock(ref, msg) if msg == " " =>
      log.debug("current value: '{}'; player sent: '{}'", currentValue, msg)
      players foreach  {
        case r if correctValue == currentValue && r == sender() => r ! Winner
        case r if correctValue == currentValue => r ! Slowpock
        case r if correctValue != currentValue && r == sender() => r ! Bustler
        case r if correctValue != currentValue => r ! Lucky
      }
      context stop self

    case Terminated =>
      // if either player exited notify others and quit gracefully
      players.filter(sender() != _).foreach(_ ! PlayerExited)
      context stop self

    case _ =>
  }

  def scheduleTick: String = {
    import context.dispatcher

    import scala.concurrent.duration._
    import scala.util.Random

    val r = Random
    val minInterval = 2000
    val maxDelta = 2000
    val interval = minInterval + r.nextInt(maxDelta)
    val diceFaces = List("1", "2", "3")
    val diceFace = diceFaces(r.nextInt(diceFaces.size))
    context.system.scheduler.scheduleOnce(interval milliseconds, self, Tick(diceFace))

    diceFace
  }
}

object Session {
  case object SessionCreated
  case class Tick(msg: String)
  case class Tock(ref: ActorRef, msg: String)
  case object Winner
  case object Slowpock
  case object Bustler
  case object Lucky
  case object PlayerExited
  class ServiceShuttingDown(msg: String) extends RuntimeException(msg)
}