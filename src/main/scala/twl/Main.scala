package twl

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import akka.event.LoggingReceive
import java.net.InetSocketAddress
import java.util.UUID

import scala.collection.mutable.ListBuffer


object Main extends App {
  val system = ActorSystem("twl")
  system.actorOf(Props[Server], "server")
}

class Server extends Actor with ActorLogging {
  import Tcp._
  import context.system
  import twl.SessionManager._

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 6600))

  val sessionManager = context.actorOf(Props[SessionManager], "session-manager")

  def receive = LoggingReceive {
    case b @ Bound(localAddress) => ()
    case CommandFailed(_: Bind) => context stop self
    case c @ Connected(remote, local) =>
      val connection = sender()
      val ref = context.actorOf(Props(classOf[ConnectionHandler], connection), "connection-handler_" + UUID.randomUUID())
      connection ! Register(ref)
      connection ! Write(ByteString("Привет! Попробую найти тебе противника\n"))
      sessionManager ! RegisterPlayer(ref)
  }
}

class ConnectionHandler(connection: ActorRef) extends Actor with ActorLogging {
  import Tcp._
  import Session._

  var session: Option[ActorRef] = None

  def receive = LoggingReceive {
    case SessionCreated =>
      session = Some(sender())
      connection ! Write(ByteString("Противник найден. Нажмите пробел, когда увидите цифру 3\n"))
    case Received(data) =>
      val recv = data.utf8String.stripSuffix("\n")
      log.debug("received from customer: '{}'", recv)
      session.foreach(_ ! Tock(self, recv))
    case Tick(msg) =>
      connection ! Write(ByteString(msg+"\n"))

    case Winner =>
      connection ! Write(ByteString("Вы нажали пробел первым и победили\n"))
      context stop self
    case Slowpock =>
      connection ! Write(ByteString("Вы не успели и проиграли\n"))
      context stop self
    case Bustler =>
      connection ! Write(ByteString("Вы поспешили и проиграли\n"))
      context stop self
    case Lucky =>
      connection ! Write(ByteString("Ваш противник поспешил и вы выйграли\n"))
      context stop self
    case PlayerExited =>
      connection ! Write(ByteString("Ваш соперник покинул игру\n"))
      context stop self

    case PeerClosed => context stop self

  }
}

object SessionManager {
  case class RegisterPlayer(ref: ActorRef)
}

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
    import scala.util.Random
    import scala.concurrent.duration._
    import context.dispatcher

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
