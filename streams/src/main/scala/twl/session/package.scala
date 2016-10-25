package twl

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import twl.player.Contract

package object session {
  type ControlSource = Source[ControlCommand, NotUsed]
  type ControlSink = Sink[ControlCommand, NotUsed]
  type SignalSource = Source[Int, NotUsed]
  type SignalSink = Sink[Int, NotUsed]

  val SIG_YOU_WIN = 0
  val SIG_YOU_LOOSE = 1
  val SIG_YOU_BUSTLER = 2
  val SIG_PEER_BUSTLER = 3
  val SIG_PEER_GONE = 4

  sealed class ControlCommand
  case class Done(c: Contract) extends ControlCommand
  case class Inactive() extends ControlCommand
}
