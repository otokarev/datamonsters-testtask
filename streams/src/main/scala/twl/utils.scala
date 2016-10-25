package twl
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub}
import akka.stream.{Materializer, SharedKillSwitch}

object utils {
  def pubSub[T](killSwitch: SharedKillSwitch)(implicit materializer: Materializer)
  // TODO: check that this structure is freed after game session completed
  = MergeHub.source[T](perProducerBufferSize = 16).via(killSwitch.flow)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both).named("pub-sub")
    .run()

}
