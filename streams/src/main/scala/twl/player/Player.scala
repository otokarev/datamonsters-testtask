package twl.player

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.stream.{KillSwitches, Materializer}
import akka.util.ByteString
import twl.session._

class Player(connection: IncomingConnection)(implicit system: ActorSystem, materializer: Materializer) {
  import system.dispatcher
  import twl.utils._

  val killSwitch = KillSwitches.shared("player-killswitch")

  val (gameSink, gameSource) = pubSub[DataType](killSwitch)
  val (sessionIn, playerOut) = pubSub[ControlType](killSwitch)
  val (playerIn, sessionOut) = pubSub[SignalType](killSwitch)

  connection.flow
    .via(killSwitch.flow).joinMat(play())(Keep.right).run()

  def close() = killSwitch.shutdown()

  private def play()(implicit system: ActorSystem, materializer: Materializer)
    = Flow[ByteString]
      .watchTermination()((_, termination) => termination.onComplete {
        _ =>
          system.log.debug("Stream terminated. Notify peers.")
          Source.single(Dead()).to(sessionIn).run()
      })
      .map(_.utf8String)
      // Debug info
      .map { msg ⇒ system.log.debug(s"Server received: $msg"); msg }
      // Read player input and communicate with session about it
      .via(handlePlayerInput)
      // Print session reaction
      .merge(handleSessionFeedback)
      // Print current game value ("1", "2" or "3")
      .merge(gameSource)
      .merge(Source.single("Привет! Попробую найти тебе противника.\n"))
      .map(ByteString(_))

  private def handleSessionFeedback = sessionOut.map { a =>

    val result = a match {
      case SIG_PEER_GONE =>
        "Ваш соперник покинул игру\n"
      case SIG_YOU_WIN =>
        "Вы нажали пробел первым и победили\n"
      case SIG_YOU_LOOSE =>
        "Вы не успели и проиграли\n"
      case SIG_PEER_BUSTLER =>
        "Ваш противник поспешил и вы выйграли\n"
      case SIG_YOU_BUSTLER =>
        "Вы поспешили и проиграли\n"
    }

    close()
    result

  } map {"\n" + _} named "handle-session-feedback"

  private def handlePlayerInput = Flow[DataType].filter(" " == _).map {a =>
        system.log.debug("SPACE received")
        Source.single(Done()).to(sessionIn).run()
        a
  } named "handle-player-input"

}

object Player {
  def apply(connection: IncomingConnection)(implicit system: ActorSystem, materializer: Materializer): Player = new Player(connection)
}
