package twl

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}

package object session {
  type DataType = String
  type SignalType = Int
  type ControlSource = Source[ControlType, NotUsed]
  type ControlSink = Sink[ControlType, NotUsed]
  type SignalSource = Source[Int, NotUsed]
  type SignalSink = Sink[Int, NotUsed]

  val SIG_YOU_WIN = 0
  val SIG_YOU_LOOSE = 1
  val SIG_YOU_BUSTLER = 2
  val SIG_PEER_BUSTLER = 3
  val SIG_PEER_GONE = 4

  sealed class ControlType
  case class Done() extends ControlType
  case class Inactive() extends ControlType
}
