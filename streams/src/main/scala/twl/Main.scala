package twl

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp._
import akka.stream.scaladsl._
import akka.stream.{actor => _, _}
import twl.session.Inactive
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

      val killSwitch = KillSwitches.shared("player-killswitch")

      val contract = Contract(
        connection = connection,
        gamePorts = pubSub(killSwitch),
        controlPorts = pubSub(killSwitch),
        signalPorts = pubSub(killSwitch),
        killSwitch = killSwitch
      )

      // initialize player output stream with initial message
      Source.single(Inactive()).to(contract.sessionIn).run()

      connection.flow
        .via(killSwitch.flow).joinMat(play(contract))(Keep.right).run()

      contract
    })
    .grouped(2 /*New session starts when at least two players connect to the game*/)
    .map(contracts => {
      import session.{CheckInputFlow, _}
      import twl.game._


      val killSwitch = KillSwitches.shared("session-killswitch")

      val game = gameSource().via(killSwitch.flow)

      val (playSink, playSource) = pubSub[String](killSwitch)

      Source.single("Противник найден. Нажмите пробел, когда увидите цифру 3\n")
        .concat(game).named("play-source").to(playSink).run()


      contracts.foreach {contract =>

        // Start game
        playSource.to(contract.gameSink).run()

        contract.playerOut.via(new CheckInputFlow(contract.gameSource)).via(contract.killSwitch.flow)
          .map {
            case true =>
              Source.single(SIG_YOU_WIN).to(contract.playerIn).run()
              contracts.filter(_ != contract).foreach { c =>
                Source.single(SIG_YOU_LOOSE).to(c.playerIn).run()
              }
              killSwitch.shutdown()
            case false =>
              Source.single(SIG_YOU_BUSTLER).to(contract.playerIn).run()
              contracts.filter(_ != contract).foreach { c =>
                Source.single(SIG_PEER_BUSTLER).to(c.playerIn).run()
              }
              killSwitch.shutdown()
          } to Sink.ignore run
      }

    }).to(Sink.ignore).named("session-manager")

}



