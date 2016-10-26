package twl

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp._
import akka.stream.scaladsl._
import akka.stream.{actor => _, _}
import twl.player.Player
import twl.session.Session

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Main extends App {
  startServer("127.0.0.1", 6600)

  def startServer(address: String, port: Int) = {
    implicit val system: ActorSystem = ActorSystem("datamonsters-testtaks-stream")
    implicit val materializer = ActorMaterializer()

    import system.dispatcher

    val connections: Source[IncomingConnection, Future[ServerBinding]] =
      Tcp().bind(address, port)

    val binding = connections.to(sessionManager).run()

    binding onComplete {
      case Success(b) ⇒
        system.log.debug(s"Server started, listening on: ${b.localAddress}")
      case Failure(e) ⇒
        system.log.debug(s"Server could not be bound to $address:$port: ${e.getMessage}")
    }
  }

  def sessionManager(implicit system: ActorSystem, materializer: Materializer)
  = Flow[IncomingConnection]
    /**
      * Instantiate a player with all in/out ports required to communicate with a session (that does not exist yet)
      */
    .map(Player(_))
    /**
      * There must be a logic to choose players for a game
      * In our simplest case the game members are two ones taken in a raw (`group(2)`) in order of their connect attempt
      */
    .grouped(2 /*New session starts when at least two players connect to the game*/)
    /**
      * Run a session per each pair of players
      */
    .to(Sink.foreach[Seq[Player]](Session(_))).named("session-manager")

}



