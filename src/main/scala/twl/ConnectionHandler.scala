package twl

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import akka.io.Tcp
import akka.util.ByteString

class ConnectionHandler(connection: ActorRef) extends Actor with ActorLogging {
  import Session._
  import Tcp._

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
