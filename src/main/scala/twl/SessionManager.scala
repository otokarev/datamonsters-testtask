package twl

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive

import scala.collection.mutable.ListBuffer

class SessionManager extends Actor with ActorLogging {
  import twl.SessionManager._

  val maxPlayerNumber = 2

  var waiting = ListBuffer[ActorRef]()

  def receive = LoggingReceive {
    case RegisterPlayer(ref) =>
      log.debug("waiting list was: {}", waiting)
      waiting += ref
      log.debug("waiting list become: {}", waiting)

      waiting.grouped(maxPlayerNumber).toList.foreach {
        case a if a.size == maxPlayerNumber => context.actorOf(Props(classOf[Session], a), "session_" + UUID.randomUUID())
        case a => waiting = a
      }
  }
}

object SessionManager {
  case class RegisterPlayer(ref: ActorRef)
}