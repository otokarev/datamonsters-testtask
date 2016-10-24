package twl.player

import akka.stream.scaladsl.Tcp.IncomingConnection
import twl.game._
import twl.session._

case class Contract(
                     connection: IncomingConnection,
                     gamePorts: Tuple2[GameSink, GameSource],
                     controlPorts: Tuple2[ControlSink, ControlSource],
                     signalPorts: Tuple2[SignalSink, SignalSource]
                   ) {
  def sessionIn = controlPorts._1
  def sessionOut = signalPorts._2
  def playerIn = signalPorts._1
  def playerOut = controlPorts._2
  def gameSource = gamePorts._2
  def gameSink = gamePorts._1

}
