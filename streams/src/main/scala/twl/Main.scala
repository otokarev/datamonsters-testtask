package twl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp._
import akka.stream.scaladsl._
import akka.stream.stage._
import akka.stream.{actor => _, _}
import akka.util.ByteString

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success}

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
      val (sink, source) =
        // TODO: check that this structure is freed after game session completed
        MergeHub.source[String](perProducerBufferSize = 16)
          .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
          .run()
      connection.handleWith(play(connection, source))
      sink
    })
    .grouped(2 /*New session starts when at least two players connect to the game*/)
    .map(sinks => {

      //FIXME: hmmm... there must be only one run per gameEngineSource
      val playGraph = Source.single("Противник найден. Нажмите пробел, когда увидите цифру 3\n")
        .concat(gameEngineSource).named("play-source").toMat(BroadcastHub.sink)(Keep.right)

      sinks.foreach {sink =>
        playGraph.run().to(sink).run()
      }
    }).to(Sink.ignore).named("session-manager")

  def play(
            connection: IncomingConnection,
            game: Source[String, NotUsed]
          ) = Flow.fromGraph(GraphDSL.create() {implicit b =>
    import GraphDSL.Implicits._

    // TODO: here we should shutdown the session and unlink session members connections

    val preprocess = b.add(Flow[ByteString]
      .map(_.utf8String)
      .map { msg ⇒ system.log.debug(s"Server received: $msg"); msg }
      .merge(game))
    val postprocess = b.add(Flow[String].map(ByteString(_)))

    val feedback = Flow[String].via(helloFlow).named("feedback")
    val process = Flow[String].via(new CheckPlayerInputFlow(game)).named("processing")

    val bcast = b.add(Broadcast[String](2))
    val merge = b.add(Merge[String](2))

    preprocess ~> bcast ~> feedback ~> merge ~> postprocess
                            bcast ~> process ~> merge

    FlowShape(preprocess.in, postprocess.out)
  })

  def gameEngineSource: Source[String, NotUsed] = Source.fromGraph(new GameEngineSourceGraph).named("game-engine")

  def checkPlayerInputFlow(playgroundSource: Source[String, NotUsed]): Graph[FlowShape[String, String], NotUsed]
  = new CheckPlayerInputFlow(playgroundSource).named("players-input-checker")

  def helloFlow = Flow.fromGraph(GraphDSL.create() {implicit b =>
    import GraphDSL.Implicits._

    val hello = Source.single("Привет! Попробую найти тебе противника.\n")

    val concat = b.add(Concat[String]())

    concat.in(0) <~ hello

    FlowShape(concat.in(1), concat.out)
  }).named("hello")

  class GameEngineSourceGraph extends GraphStage[SourceShape[String]] {
    val out: Outlet[String] = Outlet("GameEngineSourceGraph.out")

    override val shape: SourceShape[String] = SourceShape(out)

    override def createLogic(inheritedAttributes: Attributes) = new TimerGraphStageLogic(shape) {
      val buffer = mutable.Queue[String]()
      var downstreamWaiting = false

      override def preStart(): Unit = {
        reschedule()
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if (buffer.isEmpty) {
            downstreamWaiting = true
          } else {
            val v = buffer.dequeue
            system.log.debug(s"${this.getClass.getSimpleName}: pushed `$v`")
            push(out, v)
          }
        }
      })
      override protected def onTimer(timerKey: Any): Unit = {
        buffer.enqueue((Random.nextInt(3) + 1).toString)
        if (downstreamWaiting) {
          downstreamWaiting = false
          val v = buffer.dequeue()
          system.log.debug(s"${this.getClass.getSimpleName}: pushed `$v`")
          push(out, v)
          if (v == "3") completeStage()
        }
        reschedule()
      }
      def reschedule() =  {
        scheduleOnce(None, 2000 + Random.nextInt(2000) milliseconds)
        system.log.debug(s"${this.getClass.getSimpleName}: rescheduled")
      }
    }
  }

  class CheckPlayerInputFlow(s: Source[String, NotUsed])(implicit system: ActorSystem, materializer: Materializer) extends GraphStage[FlowShape[String, String]] {
    val in = Inlet[String]("CheckPlayerInputFlow.in")
    val out = Outlet[String]("CheckPlayerInputFlow.out")

    override val shape = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {

      var currentValue = ""

      override def preStart(): Unit = {

        system.log.debug(s"${this.getClass.getSimpleName}: async callback set")

        val callback = getAsyncCallback[String] {a =>
          system.log.debug(s"${this.getClass.getSimpleName}: new value set $a")
          currentValue = a
        }

        s.runForeach(callback.invoke)(materializer)
      }

      setHandler(in, new InHandler {
        override def onPush() = grab(in) match {
          case " " ⇒
            if (currentValue == "3")
              push(out, s"\nМолодец!\n")
            else {
              push(out, s"\nНемолодец!\n")
            }
          case msg =>
            system.log.debug(s"${this.getClass.getSimpleName}: Wrong character `{}` received. Ignore it.", msg)
            pull(in)
        }
      })
      setHandler(out, new OutHandler {
        override def onPull() = pull(in)
      })
    }
  }
}
