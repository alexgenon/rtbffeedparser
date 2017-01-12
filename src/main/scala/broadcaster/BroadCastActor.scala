package broadcaster

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRef}
import songrepository.SongRepositoryActor.NewSong
import domain.Song
import BroadCastActor._

/**
  * Created by alexandregenon on 12/01/17.
  * Keep a list of subscribers for new songs processed feed.
  * {@link SongRepositoryActor} is in charge of producing the new song events.
  */
class BroadCastActor extends Actor with ActorLogging{
  val subscribers = scala.collection.mutable.HashSet[ActorRef]()
  override def receive: Receive = {
    case Subscribe(subscriber) => {
      subscribers += subscriber
    }
    case Unsubscribe(subscriber) => {
      subscribers -= subscriber
    }
    case newSong : NewSong => {
      subscribers.foreach(actor => actor ! newSong)
    }

  }

}

object BroadCastActor {
  case class Subscribe(subscriber:ActorRef)
  case class Unsubscribe(subscriber:ActorRef)
}
