package twl

import akka.actor.ActorDSL._
import akka.actor.{ActorLogging, ActorRef, ActorSystem}
import akka.stream.scaladsl.Tcp._
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

object Main extends App {

  implicit val system: ActorSystem = ActorSystem("datamonsters-testtaks-stream")
  implicit val materializer = ActorMaterializer()

  val connections: Source[IncomingConnection, Future[ServerBinding]] =
    Tcp().bind("127.0.0.1", 6600)

  val sessionManager = sessionManagerActor

  def dialogSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"New connection from: ${connection.remoteAddress}")

    val play =  Source.actorRef[String](10, OverflowStrategy.fail)

    val ref = Flow[String].to(Sink.foreach(println)).runWith(play)

    sessionManager.tell("register", ref)

    val echo = Flow[ByteString]
      .map(_.utf8String)
      .merge(Source.single("Привет! Попробую найти тебе противника.\n"))
      .merge(play)
      .map(ByteString(_))

    connection.handleWith(echo)

//    val materialized = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
//      import GraphDSL.Implicits._
//
//      val merge = b.add(Merge[String](2))
//      echo ~> merge
////      play.out ~> merge ~> transform.out
//      ClosedShape
//    })

  }

  connections.toMat(dialogSink)(Keep.right).run()

  def sessionManagerActor = actor(new Act with ActorLogging {
    val maxPlayerNumber = 2

    var waiting = ListBuffer[ActorRef]()

    log.debug("SessionManager instantiated")

    become {
      case "register" =>
        log.debug("new player registered in the game: `{}`", sender())
        waiting += sender()

        waiting.grouped(maxPlayerNumber).toList.filter(_.size == maxPlayerNumber).foreach {
          a =>
            waiting --= a
            log.debug("new session is starting")
            sessionActor(a)
        }
      case a =>
        log.debug("wrong message received: {}", a)
    }
  })

  def sessionActor(sources: Seq[ActorRef]) = actor(new Act with ActorLogging {

    import context.dispatcher

    log.debug("Session instantiated")

    reschedule

    sources.foreach(_ ! "Противник найден. Нажмите пробел, когда увидите цифру 3\n")
    become({
      case i: Int =>
        if (i < 3) {
          reschedule
        }
        log.debug(s"Sending `$i` to players")
        sources.foreach { a =>
          a ! i.toString
          log.debug("`{}` sent to `{}`", i.toString, a)
        }
      case a =>
        log.debug("wrong message received: {}", a)
    })
    def reschedule = context.system.scheduler.scheduleOnce(
      Random.nextInt(2000) + 2000 milliseconds, self, Random.nextInt(3)+1
    )
  })

  /*
    def connectionsToSessionsSink = Sink.foreach[IncomingConnection] { connection =>
      println("Registering session manager..")

      val playground = Flow[ByteString].merge(Source.single(ByteString("Противник найден. Нажмите пробел, когда увидите цифру 3\n")))

      connection.handleWith(playground)
    }
    def connectionsToSessionsSink: Sink[IncomingConnection, NotUsed] = {
      println("Registering session manager..")
      val session = Flow[IncomingConnection].async.grouped(2).async.map {players => {
        val playground = generatePlaygroundFlow
        println("New session created")

        println(players.size)
        players foreach {player =>
          println("Handle with")
          player.handleWith(playground)
          println("Handle with2")
        }
        println("Handle with3")
      }}

      session.to(Sink.ignore)
    }

  def generatePlaygroundFlow: Flow[ByteString, ByteString, NotUsed] = {
    /**
      * Generate playground
      *
      * (The approach below breaks all Akka Streams concepts
      * but hardly possible that generated list will have infinite size)
      */
    var v = ""
    var playgroundSkeleton = ListBuffer[(String, FiniteDuration)]()
    while (v != "3") {
      v = (Random.nextInt(3) + 1).toString
      playgroundSkeleton += Tuple2(v, (2000 + Random.nextInt(2000)).milliseconds)
    }

    var offset = 0
    var playground = Flow[ByteString].merge(Source.single(ByteString("Противник найден. Нажмите пробел, когда увидите цифру 3\n")))
    playgroundSkeleton foreach { a =>
      playground = playground.merge(Source.single(ByteString(a._1)).delay(a._2))
    }

    println("New playground generated")
    playground
  }
    */
}
