package twl.session

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{KillSwitches, Materializer}
import twl.player.Player

class Session(contracts: Seq[Player])(implicit system: ActorSystem, materializer: Materializer) {
  import twl.game._
  import twl.utils._

  val killSwitch = KillSwitches.shared("session-killswitch")

  val game = GameSource().via(killSwitch.flow)

  val (playSink, playSource) = pubSub[String](killSwitch)

  // FIXME: this message is visible for first connected player only
  Source.single("Противник найден. Нажмите пробел, когда увидите цифру 3\n")
    .concat(game).named("play-source").to(playSink).run()

  contracts.foreach {contract =>
    // Start game
    playSource.to(contract.gameSink).run()

    contract.playerOut.via(contract.killSwitch.flow).via(new CheckInputFlow(contract.gameSource))
      .map({
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
      }).to(Sink.ignore).run()
  }

  def close(): Unit = {
    killSwitch.shutdown()
    contracts.foreach {
      _.killSwitch.shutdown()
    }
  }
}

object Session {
  def apply(contracts: Seq[Player])(implicit system: ActorSystem, materializer: Materializer): Session = new Session(contracts)
}
