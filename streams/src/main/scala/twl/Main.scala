package twl

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp._
import akka.stream.scaladsl._
import akka.stream.{actor => _, _}
import twl.utils._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Main extends App {
  implicit val system: ActorSystem = ActorSystem("datamonsters-testtaks-stream")
  implicit val materializer = ActorMaterializer()

  startServer("127.0.0.1", 6600)

  def startServer(address: String, port: Int)(implicit system: ActorSystem, materializer: Materializer) = {
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
    .map(connection => {
      import player._

      val (sink, source) = pubSub()

      connection.handleWith(play(connection, source))

      sink
    })
    .grouped(2 /*New session starts when at least two players connect to the game*/)
    .map(sinks => {
      import twl.game._

      val (sink, source) = pubSub()

      val game = gameSource()

      val gameGraph = Source.single("Противник найден. Нажмите пробел, когда увидите цифру 3\n")
        .concat(game).named("play-source").to(sink).run()

      sinks.foreach {sink =>
        source.to(sink).run()
      }
    }).to(Sink.ignore).named("session-manager")
}



