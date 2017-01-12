package songrepository

import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging}
import domain.Song
import SongRepositoryActor._

/**
  * Created by alexandregenon on 08/01/17.
  * Receive the recents poll results from FeedPollingActor and reconcile the list with the already stored ones
  */
class SongRepositoryActor extends Actor with ActorLogging{
  val playedSongs = scala.collection.mutable.Map[LocalDateTime,Song]()
  val aroadCastActor = context.actorSelection("/user/broadCastActor")
  def mergeList(songs: List[Song]) = {
    val newSongs = songs.filterNot{ s=> playedSongs.get(s.start).isDefined }
    newSongs.foreach{s =>
      log.debug("New song: {}",s)
      playedSongs += (s.start -> s)
      // publish s
    }
  }

  override def receive: Receive = {
    case NewFeed(songs:List[Song]) => mergeList(songs)
    case GetFullList => sender ! FullList(playedSongs.values.toList)
  }

}

object SongRepositoryActor {
  case class NewFeed(songs: List[Song])
  case object GetFullList
  case class  FullList(songs:List[Song])
  case class NewSong(song:Song)
}