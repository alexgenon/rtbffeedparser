package main

import akka.actor.{ActorSystem, Props}
import broadcaster.BroadCastActor
import com.typesafe.config.ConfigFactory
import feedpolling.FeedPollingActor
import feedpolling.FeedPollingActor.UpdateURL
import songrepository.SongRepositoryActor
import web.RestApi

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by alexandregenon on 08/01/17.
  * Maestro is the orchestrator of the whole Actor system. This is where the setup is make
  * Currently, 2 actors are fired up :
  * <ul>
  *   <li> {@link feedpolling.FeedPollingActor} that periodically poll the configured URL</li>
  *   <li> {@link reconciliation.ReconciliationActor} that receives a list of {@link domain.Song songs} and filters out
  *   the ones already received </li>
  * </ul>
  */
object Maestro {
  implicit val akkaSystem = ActorSystem("RTBFFeedParser")
  val config = ConfigFactory.load
  import akkaSystem.dispatcher

  def startup = {
    val defaultPollURL = config.getString("rtbffeedparser.feedpoller.defaults.url")
    val defaultPollStationId = config.getString("rtbffeedparser.feedpoller.defaults.stationid")
    val songRepositoryActor = akkaSystem.actorOf(Props(classOf[SongRepositoryActor]),"songRepositoryActor")
    val feedPollingActor = akkaSystem.actorOf(Props(classOf[FeedPollingActor],songRepositoryActor),"feedPollingActor")
    val broadCastActor = akkaSystem.actorOf(Props(classOf[BroadCastActor]),"broadCastActor")
    val restApi = new RestApi
    restApi.start
    feedPollingActor ! UpdateURL(defaultPollURL,defaultPollStationId)
    akkaSystem.whenTerminated.andThen { case e => restApi.stop}
  }

  def main(args: Array[String]): Unit = {
    println("Starting Up The system")
    startup
  }
}


