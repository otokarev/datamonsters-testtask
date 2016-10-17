package twl

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import akka.io.{IO, Tcp}
import akka.util.ByteString

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
