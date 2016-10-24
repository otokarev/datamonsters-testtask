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
    .map(connection => {
      import player.{Contract, _}

      val contract = Contract(
        connection = connection,
        gamePorts = pubSub(),
        controlPorts = pubSub(),
        signalPorts = pubSub()
      )
      connection.handleWith(play(contract))

      contract
    })
    .grouped(2 /*New session starts when at least two players connect to the game*/)
    .map(contracts => {
      import session._
      import twl.game._


      val game = gameSource()

      val (playSink, playSource) = pubSub[String]()

      Source.single("Противник найден. Нажмите пробел, когда увидите цифру 3\n")
        .concat(game).named("play-source").to(playSink).run()


      contracts.foreach {contract =>
        playSource.to(contract.gameSink).run()
        Source.single("").merge(contract.playerOut).expand(Iterator.continually(_)).zip(playSource).map {a =>
          system.log.debug("Player input: `{}`; Current value: `{}`", a._1, a._2)
          a
        } map {
          case (Gocha(c), "3") =>
            Source.single(SIG_YOU_WIN).to(c.playerIn).run()
            contracts.filter(_ != c).foreach(c =>
              Source.single(SIG_YOU_LOOSE).to(c.playerIn).run()
            )
          case (Gocha(c), _) =>
            Source.single(SIG_YOU_BUSTLER).to(c.playerIn).run()
            contracts.filter(_ != c).foreach(c =>
              Source.single(SIG_PEER_BUSTLER).to(c.playerIn).run()
            )
        } to Sink.ignore
      }

    }).to(Sink.ignore).named("session-manager")

}



