package twl.session

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler}
import twl.player.Player

class Session(private[session] val players: Seq[Player])(implicit system: ActorSystem, materializer: Materializer) {
  import twl.game._
  import twl.utils._

  val killSwitch = KillSwitches.shared("session-killswitch")

  val game = GameSource().via(killSwitch.flow)

  // Create Hub for Play to subscribe to
  val (playSink, playSource) = pubSub[String](killSwitch)

  players.foreach { player =>
    // Connect the player to the game source
    playSource.to(player.gameSink).run()

    // Connect the player to session engine
    player.playerOut.via(player.killSwitch.flow).to(SessionSink(player, this)).run()
  }

  Source.single("Противник найден. Нажмите пробел, когда увидите цифру 3\n")
    .concat(game).named("play-source").to(playSink).run()

  def closeAll(): Unit = {
    killSwitch.shutdown()
    players.foreach {
      _.killSwitch.shutdown()
    }
  }

  def close() = killSwitch.shutdown()

  class SessionSink(player: Player, session: Session)(implicit system: ActorSystem, materializer: Materializer)
    extends GraphStage[SinkShape[ControlType]] {

    val players = session.players

    val in = Inlet[ControlType]("SessionSink.in")

    override val shape = SinkShape(in)

    override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {

      var currentValue = ""

      override def preStart(): Unit = {

        // This requests one element at the Sink startup
        pull(in)

        // Async callback setup
        system.log.debug(s"${this.getClass.getSimpleName}: async callback set")

        val callback = getAsyncCallback[String] {a =>
          system.log.debug(s"${this.getClass.getSimpleName}: new value set $a")
          currentValue = a
        }

        player.gameSource.runForeach(callback.invoke)(materializer)
      }

      setHandler(in, new InHandler {
        override def onPush() = {
          grab(in) match {
            case Done() ⇒
              system.log.debug("Done received from player")
              if (currentValue == "3") {
                Source.single(SIG_YOU_WIN).to(player.playerIn).run()(materializer)
                players.filter(_ != player).foreach { c =>
                  Source.single(SIG_YOU_LOOSE).to(c.playerIn).run()(materializer)
                }
              } else {
                Source.single(SIG_YOU_BUSTLER).to(player.playerIn).run()(materializer)
                players.filter(_ != player).foreach { c =>
                  Source.single(SIG_PEER_BUSTLER).to(c.playerIn).run()(materializer)
                }
              }
              session.close()
            case Dead() =>
              system.log.debug("Dead received from player")
              players.filter(_ != player).foreach { c =>
                Source.single(SIG_PEER_GONE).to(c.playerIn).run()(materializer)
              }
              session.close()
            case msg =>
              system.log.debug(s"${this.getClass.getSimpleName}: Wrong character `{}` received. Ignore it.", msg)
          }
          pull(in)
        }
      })
    }
  }
  object SessionSink {
    def apply(player: Player, session: Session) = new SessionSink(player, session)
  }
}

object Session {
  def apply(contracts: Seq[Player])(implicit system: ActorSystem, materializer: Materializer): Session = new Session(contracts)
}
