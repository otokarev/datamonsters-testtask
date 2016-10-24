package twl
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub}

object utils {
  def pubSub[T]()(implicit materializer: Materializer)
  // TODO: check that this structure is freed after game session completed
  = MergeHub.source[T](perProducerBufferSize = 16)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()

}
