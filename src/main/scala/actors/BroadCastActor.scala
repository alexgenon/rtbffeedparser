package actors

import actors.BroadCastActor._
import akka.actor.{Actor, ActorLogging, ActorRef}
import SongRepositoryActor.NewSong
import domain.Song

/**
  * Created by alexandregenon on 12/01/17.
  * Keep a list of subscribers for new songs processed feed.
  * {@link SongRepositoryActor} is in charge of producing the new song events.
  */
class BroadCastActor extends Actor with ActorLogging {
  var subscriber: Option[ActorRef] = None

  override def receive: Receive = {
    case Subscribe(subscriber) =>{

      this.subscriber = Some(subscriber)
    }

    case Unsubscribe(subscriber) =>
      this.subscriber = None

    case newSong : NewSong =>
      subscriber.foreach(actor => actor ! newSong)

    case "ping" =>
      subscriber.foreach(actor => actor ! NewSong(Song.exampleSong))

    case message =>
      log.warning(s"Unexpected message received $message")
  }
}

object BroadCastActor {
  case class Subscribe(subscriber:ActorRef)
  case class Unsubscribe(subscriber:ActorRef)
}
