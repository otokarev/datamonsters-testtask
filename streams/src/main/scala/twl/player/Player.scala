package twl.player

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Merge, Source}
import akka.stream.{FlowShape, KillSwitches, Materializer}
import akka.util.ByteString
import twl.session._

class Player(connection: IncomingConnection)(implicit system: ActorSystem, materializer: Materializer) {
  import twl.utils._

  val killSwitch = KillSwitches.shared("player-killswitch")

  val (gameSink, gameSource) = pubSub[DataType](killSwitch)
  val (sessionIn, playerOut) = pubSub[ControlType](killSwitch)
  val (playerIn, sessionOut) = pubSub[SignalType](killSwitch)

  // initialize player output stream with initial message
  Source.single(Inactive()).to(sessionIn).run()

  connection.flow
    .via(killSwitch.flow).joinMat(play())(Keep.right).run()

  def close() = killSwitch.shutdown()

  def play()(implicit system: ActorSystem, materializer: Materializer) = Flow.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val preprocess = b.add(Flow[ByteString]
      .map(_.utf8String)
      .map { msg ⇒ system.log.debug(s"Server received: $msg"); msg }
      .merge(gameSource))


    val sessionFeedback = sessionOut.map {a =>

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

      killSwitch.shutdown()
      result

    } map {"\n" + _}

    val postprocess = b.add(Flow[String].merge(Source.single("Привет! Попробую найти тебе противника.\n")).map(ByteString(_)))

    val feedback = Flow[String]
      .merge(sessionFeedback).named("feedback")

    val process = Flow[String].map {a =>
      a match {
        case " " =>
          system.log.debug("SPACE received")
          Source.single(Done()).to(sessionIn).run()
        case _ =>
          system.log.debug("Bullshit received: `{}`", _)
      }
      ""
    } named "processing"

    val bcast = b.add(Broadcast[String](2))
    val merge = b.add(Merge[String](2))

    preprocess ~> bcast ~> feedback ~> merge ~> postprocess
    bcast ~> process ~> merge

    FlowShape(preprocess.in, postprocess.out)
  })

}

object Player {
  def apply(connection: IncomingConnection)(implicit system: ActorSystem, materializer: Materializer): Player = new Player(connection)
}


