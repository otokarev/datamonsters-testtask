package twl.game

import akka.actor.ActorSystem
import akka.stream.stage.{GraphStage, OutHandler, TimerGraphStageLogic}
import akka.stream.{Attributes, Outlet, SourceShape}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Random

class GameEngineSourceGraph(implicit system: ActorSystem) extends GraphStage[SourceShape[String]] {
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
