package feedpolling

import domain.Song
import rtbfjsonprotocol.RtbfJsonProtocol
import feedpolling.FeedPollingActor._
import songrepository.SongRepositoryActor.NewFeed
import java.net.URL

import akka.actor.{ActorRef, LoggingFSM}
import akka.pattern.pipe

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Created by alexandregenon on 07/01/17.
  * Periodically fetches data from the configured URL
  * Send the data to the subscribers.
  * No reconciliation is performed here.
  */
trait FeedPollingIO {
  //todo : refactor to use akka-http
  //todo : proper validation check for URL
  def isUrlValid(url:URL) =  true
  def pollFeed (targetUrl:URL):String= {
    val result = scala.io.Source.fromURL(targetUrl)
    result.mkString
  }
}

class FeedPollingActor (reconciliationActor: ActorRef) extends LoggingFSM[State,Config] with FeedPollingIO{

  import context.dispatcher
  def isConfigValid(config:Config): Boolean = config.urlPrefix.exists(isUrlValid)

  def checkConfigAndMoveOn(config:Config) = {
    if(isConfigValid(config)) {
      goto(Idle) using config
    }
    else stay using config
  }

  private def setTime: Unit = {
    setTimer(POLLTIMERNAME, TimeToPoll, stateData.timeInterval, repeat = false)
  }

  /**
    * Actual FSM
    */
  startWith(Configuring,Config(60 seconds,None))

  when(Configuring) {
    case Event(UpdatePollInterval(newInterval),currentConfig) => {
      val newConfig = currentConfig.copy(timeInterval =  newInterval)
      checkConfigAndMoveOn(newConfig)
    }
    case Event(UpdateURL(newUrl,newStationId),currentConfig) => {
      val newConfig = Try(currentConfig.copy(urlPrefix = Some(new URL(s"$newUrl?rpId=$newStationId"))))
      newConfig match {
        case Success(config) => {
          checkConfigAndMoveOn(config)
        }
        case Failure(e) => stay replying Error(e.toString)
      }
    }

    case Event(PollNow,currentConfig) => {
      log.warning("Attempt to poll while not fully configured : config is {} ",currentConfig.toString)
      stay replying Error("Not fully configured yet")
    }
  }

  when(Idle) {
    case Event(PollNow, currentConfig) => {
      cancelTimer(POLLTIMERNAME)
      goto(Polling)
    }
    case Event(TimeToPoll,currentConfig) => {
      log.debug("Time to poll with config: {}",currentConfig)
      goto(Polling)
    }
  }

  when(Polling) {
    case Event(PollNow,currentConfig) => stay
    case Event(feed:Feed, currentConfig) => {
      log.debug("Feed received {}",feed)
      //todo delegate parsing to another actor
      val listOfSongsTried = RtbfJsonProtocol.parseFeed(feed)
      listOfSongsTried match {
        case Success(listOfSongs) => reconciliationActor ! NewFeed(listOfSongs)
        case Failure(e) => log.warning("Feed could not be parsed, error: {}",e)
        //todo circuit breaker (?)
      }
      goto(Idle)
    }
  }

  onTransition {
    case _ -> Polling => {
      //todo circuit breaker pattern !!!
      // We come from Idle => we are sure that urlPrefix is not empty
      val url = stateData.urlPrefix.get
      log.info("Polling feed : {}",url)
      val futureFeed = Future(pollFeed(url))
      pipe(futureFeed) to self

    }
    case _ -> Idle => {
      setTime
    }
  }

}

object FeedPollingActor {
  private[feedpolling] val POLLTIMERNAME = "PollInterval"
  case object TimeToPoll
  case object PollNow
  case class UpdatePollInterval(timeInterval: FiniteDuration)
  case class UpdateURL(url: String, stationId: String)
  case class Error(msg: String)
  type Feed= String

  sealed trait State
  case object Idle extends State
  case object Polling extends State
  case object Configuring extends State

  case class Config(timeInterval: FiniteDuration, urlPrefix: Option[URL])
}
