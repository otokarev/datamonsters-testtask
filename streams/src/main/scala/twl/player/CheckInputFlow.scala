package twl.player

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

class CheckInputFlow(s: Source[String, NotUsed])(implicit system: ActorSystem, materializer: Materializer) extends GraphStage[FlowShape[String, String]] {
  val in = Inlet[String]("CheckInputFlow.in")
  val out = Outlet[String]("CheckInputFlow.out")

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
