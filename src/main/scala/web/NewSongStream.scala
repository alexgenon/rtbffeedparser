package web

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}

/**
  * Created by alexandregenon on 09/01/17.
  */
object NewSongStream {
  lazy val receivingQueue = Source.queue(100,OverflowStrategy.backpressure)
  val testReceivingQueue = receivingQueue.toMat(Sink.foreach(println))
}
