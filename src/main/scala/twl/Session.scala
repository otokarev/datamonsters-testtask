package twl

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.event.LoggingReceive

import scala.collection.mutable.ListBuffer

class Session(players: ListBuffer[ActorRef]) extends Actor with ActorLogging {
  import twl.Session._

  val trigger = " "
  val correct = "3"

  log.info("Session starts with players: {}", players)

  players.foreach {a =>
    a ! SessionCreated
    context.watch(a)
  }

  var current = reschedule(Tick(""))

  def receive = LoggingReceive {
    case t @ Tick(msg) =>
      current = reschedule(t)
      log.debug("New value set: '{}'", current)
      players.foreach {_ ! t}

    case Tock(ref, msg) if msg == trigger =>
      log.debug("current value: '{}'; player sent: '{}'", current, msg)
      players foreach  {
        case r if current == correct && r == sender() => r ! Winner
        case r if current == correct => r ! Slowpock
        case r if current != correct && r == sender() => r ! Bustler
        case r if current != correct => r ! Lucky
      }
      context stop self

    case Tock(ref, msg) =>
      log.debug("Wrong trigger received")

    case Terminated(ref) =>
      handleTerminated(ref)

  }

  def handleTerminated(ref: ActorRef) = {
    context.unwatch(ref)
    // Deregister player
    players -= ref
    log.info("Player {} deregistered from the session", ref)
    // Notify others players that their competitor exited
    players.foreach(_ ! PlayerExited)

    // Close session if no registered player left
    if (players.isEmpty) {
      log.info("Session closed")
      context stop self
    }
  }

  def reschedule(t: Tick) = {
    import context.dispatcher

    import scala.concurrent.duration._
    import scala.util.Random

    val r = Random
    val minInterval = 2000
    val maxDelta = 2000
    val interval = minInterval + r.nextInt(maxDelta)
    val diceFaces = List("1", "2", "3")
    val diceFace = diceFaces(r.nextInt(diceFaces.size))

    if (t.msg != correct)
      context.system.scheduler.scheduleOnce(interval milliseconds, self, Tick(diceFace))

    t.msg
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