package main

import actors.{BroadCastActor, SongRepositoryActor,FeedPollingActor}
import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import actors.FeedPollingActor.UpdateURL
import api.RestApi

/**
  * Created by alexandregenon on 08/01/17.
  * Maestro is the orchestrator of the whole Actor system. This is where the setup is make
  * Currently, 3 actors are fired up :
  * <ul>
  *   <li> {@link actors.FeedPollingActor} that periodically poll the configured URL</li>
  *   <li> {@link actors.SongRepositoryActor} that receives a list of {@link domain.Song songs} and filters out
  *   the ones already received </li>
  *   <li> {@link actors.BroadCastActor} which is broadcasting new songs to websocket clients
  * </ul>
  * Furthermore, the restApi is fired here as well
  */
object Maestro {
  implicit val akkaSystem = ActorSystem("RTBFFeedParser")
  val config = ConfigFactory.load
  import akkaSystem.dispatcher

  def startup = {
    val defaultPollURL = config.getString("rtbffeedparser.feedpoller.defaults.url")
    val defaultPollStationId = config.getString("rtbffeedparser.feedpoller.defaults.stationid").toInt
    val songRepositoryActor = akkaSystem.actorOf(SongRepositoryActor.props(defaultPollStationId),"songRepositoryActor")
    val feedPollingActor = akkaSystem.actorOf(Props(classOf[FeedPollingActor],songRepositoryActor),"feedPollingActor")
    val broadCastActor = akkaSystem.actorOf(Props(classOf[BroadCastActor]),"broadCastActor")
    val restApi = new RestApi
    restApi.start
    feedPollingActor ! UpdateURL(defaultPollURL,defaultPollStationId.toString)
    akkaSystem.whenTerminated.andThen { case e => restApi.stop}
  }

  def main(args: Array[String]): Unit = {
    println("Starting Up The system")
    startup
  }
}


