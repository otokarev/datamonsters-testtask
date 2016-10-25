package twl

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Merge, Source}
import akka.stream.{FlowShape, Materializer}
import akka.util.ByteString


package object player {

  def play(contract: Contract)(implicit system: ActorSystem, materializer: Materializer) = Flow.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._
    import session._

    val preprocess = b.add(Flow[ByteString]
      .map(_.utf8String)
      .map { msg ⇒ system.log.debug(s"Server received: $msg"); msg }
      .merge(contract.gameSource))


    val sessionFeedback = contract.sessionOut.map {a =>

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

      contract.killSwitch.shutdown()
      result

    } map {"\n" + _}

    val postprocess = b.add(Flow[String].map(ByteString(_)))

    val feedback = Flow[String].via(helloFlow).merge(sessionFeedback).named("feedback")
    val process = Flow[String].map {a => a match {
        case " " =>
          system.log.debug("SPACE received")
          Source.single(Done(contract)).to(contract.sessionIn).run()
        case _ =>
      }
      ""
    } named "processing"

    val bcast = b.add(Broadcast[String](2))
    val merge = b.add(Merge[String](2))

    preprocess ~> bcast ~> feedback ~> merge ~> postprocess
    bcast ~> process ~> merge

    FlowShape(preprocess.in, postprocess.out)
  })

  private def helloFlow = Flow.fromGraph(GraphDSL.create() {implicit b =>
    import GraphDSL.Implicits._

    val hello = Source.single("Привет! Попробую найти тебе противника.\n")

    val concat = b.add(Concat[String]())

    concat.in(0) <~ hello

    FlowShape(concat.in(1), concat.out)
  }).named("hello")

}
