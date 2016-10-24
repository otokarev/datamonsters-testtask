package twl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source

package object game {

  def gameSource()(implicit system: ActorSystem, materializer: Materializer) = Source.fromGraph(new GameEngineSourceGraph).named("game-engine")
}
