package twl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

package object game {
  type GameSource = Source[String, NotUsed]
  type GameSink = Sink[String, NotUsed]

  def gameSource()(implicit system: ActorSystem, materializer: Materializer) = Source.fromGraph(new GameEngineSourceGraph).named("game-engine")
}
