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
import scala.util.{Failure, Random, Success}

object Main extends App {

  implicit val system: ActorSystem = ActorSystem("datamonsters-testtaks-stream")
  implicit val materializer = ActorMaterializer()

//  val sessionManager = sessionManagerActor

  startServer("127.0.0.1", 6600)

  def startServer(address: String, port: Int)(implicit system: ActorSystem, materializer: Materializer) = {
    import system.dispatcher

    val connections: Source[IncomingConnection, Future[ServerBinding]] =
      Tcp().bind(address, port)

    val binding = connections.to(connectionsSink).run()

    binding onComplete {
      case Success(b) ⇒
        system.log.debug(s"Server started, listening on: ${b.localAddress}")
      case Failure(e) ⇒
        system.log.debug(s"Server could not be bound to $address:$port: ${e.getMessage}")
    }
  }

  def connectionsSink(implicit system: ActorSystem, materializer: Materializer) = Sink.foreach[IncomingConnection] { connection =>
    system.log.debug(s"New connection from: ${connection.remoteAddress}")

    connection.handleWith(serverLogic(connection))
  }

  def serverLogic (conn: Tcp.IncomingConnection)(implicit system: ActorSystem, materializer: Materializer): Flow[ByteString, ByteString, NotUsed]
  = Flow.fromGraph(GraphDSL.create() { implicit b ⇒
    import GraphDSL.Implicits._

    val welcome = Source.single(ByteString("Привет! Попробую найти тебе противника.\n"))

    val playgroundSourceGraph = new PlaygroundSourceGraph

    val playgroundSource = Source.fromGraph(playgroundSourceGraph)
    val playFlowForConsole = Flow[String]
    val playFlowForChecker = Flow[String]

    //val checkPlayerInputFlow = new CheckPlayerInputFlow(Source.empty[String].via(playFlowForChecker))
    //val checkPlayerInputFlow = new CheckPlayerInputFlow(playgroundSource)

    val logic = b.add(Flow[ByteString]
      .map(_.utf8String)
      .map { msg ⇒ system.log.debug(s"Server received: $msg"); msg }
      //.via(checkPlayerInputFlow)
      .merge(playgroundSource)
      .map(ByteString(_)))

    val concat = b.add(Concat[ByteString]())

    /*
     * WHY IT STOPS WORKING WHEN I UNCOMMENT NEXT LINE???
     */
    //val bcastPlaySource = b.add(Broadcast[String](2))
    ////playgroundSource ~> bcastPlaySource ~> playFlowForChecker
    //playgroundSource ~> bcastPlaySource ~> Sink.ignore
    ////                    bcastPlaySource.out(1) ~> playFlowForConsole
    //                    bcastPlaySource ~> Sink.ignore

    welcome ~> concat.in(0)
    logic.outlet ~> concat.in(1)

    FlowShape(logic.in, concat.out)
  })

  class PlaygroundSourceGraph extends GraphStage[SourceShape[String]] {
    val out: Outlet[String] = Outlet("PlaygroundSourceGraph.out")

    override val shape: SourceShape[String] = SourceShape(out)

    override def createLogic(inheritedAttributes: Attributes) = new TimerGraphStageLogic(shape) {
      val buffer = mutable.Queue[String]()
      var downstreamWaiting = false

      override def preStart(): Unit = {
        reschedule
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
        reschedule
      }
      def reschedule = {
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

//  def sessionManagerActor = actor(new Act with ActorLogging {
//    val maxPlayerNumber = 2
//
//    var waiting = ListBuffer[ActorRef]()
//
//    log.debug("SessionManager instantiated")
//
//    become {
//      case "register" =>
//        log.debug("new player registered in the game: `{}`", sender())
//        waiting += sender()
//
//        waiting.grouped(maxPlayerNumber).toList.filter(_.size == maxPlayerNumber).foreach {
//          a =>
//            waiting --= a
//            log.debug("new session is starting")
//            sessionActor(a)
//        }
//      case a =>
//        log.debug("wrong message received: {}", a)
//    }
//  })
//
//  def sessionActor(sources: Seq[ActorRef]) = actor(new Act with ActorLogging {
//
//    import context.dispatcher
//
//    log.debug("Session instantiated")
//
//    reschedule
//
//    sources.foreach(_ ! "Противник найден. Нажмите пробел, когда увидите цифру 3\n")
//
//    var current = 0.toString
//    become({
//      case i: Int =>
//        if (i < 3) {
//          reschedule
//        }
//        current = i.toString
//        sources.foreach { a =>
//          a ! current
//          log.debug("`{}` sent to `{}`", current, a)
//        }
//      case ' ' =>
//        if (current == 3.toString) {
//          sources.foreach {
//            case a if a == sender() => a ! "Вы нажали пробел первым и победили\n"
//            case a => a ! "Вы не успели и проиграли\n"
//          }
//        } else {
//          sources.foreach {
//            case a if a == sender() => a ! "Вы поспешили и проиграли\n"
//            case a => a ! "Ваш противник поспешил и вы выйграли\n"
//          }
//        }
//      case a =>
//        log.debug("wrong message received: {}", a)
//    })
//    def reschedule = context.system.scheduler.scheduleOnce(
//      Random.nextInt(2000) + 2000 milliseconds, self, Random.nextInt(3)+1
//    )
//  })
//
//  def connectionsSink2 = Sink.foreach[IncomingConnection] { connection =>
//    system.log.debug(s"New connection from: ${connection.remoteAddress}")
//
//    val play =  Source.actorRef[String](10, OverflowStrategy.fail)
//      .mapMaterializedValue(
//        /*
//         * Register play source in the session manager
//         *
//         * After a second player been found the session manager will create a session for a new game
//         * and that session starts sending numbers to the play source actor
//         */
//        ref => sessionManager.tell("register", ref)
//      )
//
//    val echo = Flow[ByteString]
//      .map(_.utf8String)
//      .merge(Source.single("Привет! Попробую найти тебе противника.\n"))
//      .merge(play)
//      .map(ByteString(_))
//
//    connection.handleWith(echo)
//  }
}
