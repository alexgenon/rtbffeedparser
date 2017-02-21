package songrepository

import domain.Song

import scala.concurrent.duration._
import java.time.LocalDateTime
import java.time.temporal.{ChronoUnit, TemporalUnit}

import actors.SongRepositoryActor
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestFSMRef, TestKit}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

/**
  * Created by alexandregenon on 08/01/17.
  */
/*class ReconciliationSpec extends TestKit(ActorSystem("TestActorSystem"))
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  import SongRepositoryActor._
  import system.dispatcher
  implicit val timeout = Timeout(5 seconds)

  val reconciliationActor = TestActorRef(new SongRepositoryActor)

  val t0 = LocalDateTime.now
  val minutes = ChronoUnit.MINUTES
  val test1List = List(
    Song(artist = "Jackson",song="Thriller",imageURL = "",start = t0,end = t0.plus(2,minutes)),
    Song(artist = "Coldplay", song="Paradise",imageURL = "",start = t0.plus(3,minutes),end=t0.plus(5,minutes))
  )

  val test2List = List(
    Song(artist = "Coldplay", song="Paradise",imageURL = "",start = t0.plus(3,minutes),end=t0.plus(5,minutes)),
    Song(artist = "Coldplay", song="Paradise",imageURL = "",start = t0.plus(6,minutes),end=t0.plus(9,minutes)) ,
    Song(artist = "Lost Frequencies", song="JeSaisPas",imageURL = "",start = t0.plus(10,minutes),end=t0.plus(12,minutes)),
    Song(artist = "Jackson",song="Thriller",imageURL = "",start = t0,end = t0.plus(2,minutes))
  )

  def checkSpecificSong(receivedList: List[Song]): Boolean = {
    val songAt3Min = receivedList.find(s => s.start == t0.plus(3, minutes))
    songAt3Min.exists(s => s.song == "Paradise")
  }

  "Reconciliation actor" must "accept new songs" in {
    reconciliationActor ! NewFeed(test1List)
    //todo : once pub/sub is setup, check proper publication
    reconciliationActor ! GetFullList
    val receivedList = expectMsgClass(classOf[FullList]).songs
    assert(test1List.size ==receivedList.asInstanceOf[List[Song]].size )
    assert(checkSpecificSong(receivedList))
  }

  it must "accept a new Feed and do the correct merge" in {
    reconciliationActor ! NewFeed(test2List)
    //todo : once pub/sub is setup, check proper publication
    reconciliationActor ! GetFullList
    // Start Time should be the primary key for the full list
    val mergedList = (test2List ++ test1List).groupBy(s => s.start).keys
    val receivedList = expectMsgClass(classOf[FullList]).songs
    assert(receivedList.asInstanceOf[List[Song]].size == mergedList.size)
    assert(checkSpecificSong(receivedList))
  }
  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)
}*/
