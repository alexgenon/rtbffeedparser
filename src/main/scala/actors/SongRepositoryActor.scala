package actors

import java.time.LocalDateTime

import actors.SongRepositoryActor._
import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import domain.Song

/**
  * Created by alexandregenon on 08/01/17.
  * Receive the recents poll results from FeedPollingActor and reconcile the list with the already stored ones
  */
class SongRepositoryActor (stationId: Int) extends PersistentActor with ActorLogging{
  val broadCastActor = context.actorSelection("/user/broadCastActor")

  override def persistenceId: String = "songRepository_"+stationId
  var playedSongs = scala.collection.mutable.Map[LocalDateTime,Song]()

  def skimFeed(songs: List[Song]):List[Song] = songs.filterNot{ s=> playedSongs.get(s.start).isDefined }
  def addSong(ns:NewSong):Unit = { playedSongs += (ns.song.id -> ns.song)}
  def commitSong(ns:NewSong):Unit = {
    addSong(ns)
    log.debug("New song: {}",ns.song)
    broadCastActor ! ns
  }

  override def receiveCommand: Receive = {
    case NewFeed(feed:List[Song]) => {
      log.debug("Received feed {}",feed)
      skimFeed(feed).foreach (song => { persist(NewSong(song)) (commitSong) })
      saveSnapshot(playedSongs)
    }
    case GetFullList => sender ! FullList(playedSongs.values.toList)
    case SaveSnapshotSuccess(metadata) => log.debug("Successfully save a snapshot {}",metadata)
    case SaveSnapshotFailure(metadata,reason)=>log.error("could not save snapshot {}, because of {}",metadata,reason)
    case ResetRepository => {
      persist(ResetRepository) (_ => playedSongs.clear)
    }
  }

  override def receiveRecover: Receive = {
    case ns:NewSong => {
      log.debug("Recovery for {}",ns.song)
      addSong(ns)
    }
    case SnapshotOffer(metadata, offeredSnapshot) => {
      playedSongs = offeredSnapshot.asInstanceOf[scala.collection.mutable.Map[LocalDateTime,Song]]
      log.debug("(SnapshotOffer) - Recovered {}", playedSongs)
    }
  }

}

object SongRepositoryActor {
  case class NewFeed(songs: List[Song])
  case object GetFullList
  case object ResetRepository
  case class  FullList(songs:List[Song])
  case class NewSong(song:Song)
  def props(stationId:Int):Props = Props(new SongRepositoryActor(stationId))
}