package twl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Merge, Source}
import akka.stream.{FlowShape, Materializer}
import akka.util.ByteString


package object player {

  def play(
            connection: IncomingConnection,
            game: Source[String, NotUsed]
          )(implicit system: ActorSystem, materializer: Materializer) = Flow.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    // TODO: here we should shutdown the session and unlink session members connections

    val preprocess = b.add(Flow[ByteString]
      .map(_.utf8String)
      .map { msg ⇒ system.log.debug(s"Server received: $msg"); msg }
      .merge(game))
    val postprocess = b.add(Flow[String].map(ByteString(_)))

    val feedback = Flow[String].via(helloFlow).named("feedback")
    val process = Flow[String].via(new CheckInputFlow(game)).named("processing")

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
