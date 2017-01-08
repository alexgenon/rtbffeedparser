package web

import songrepository.SongRepositoryActor.{FullList, GetFullList}

import scala.concurrent.duration._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.Http
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.json4s.{DefaultFormats, Formats, Serializer, native,TypeInfo}
import org.json4s.JsonAST.{JString,JValue}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import feedpolling.FeedPollingActor.PollNow

import scala.concurrent.Future



/**
  * Created by alexandregenon on 08/01/17.
  * Provide the rest api for the clients
  * Also support websocks
  * The song to json marshalling is also implemented here
  * in a dedicated protocol trait
  */

/**
  * SongToJSonMarshaling, inspired by
  * {@link https://github.com/hseeberger/akka-http-json/}
  *
  */

trait SongToJsonMarshalling {
  class LocalDateTimeSerializer extends Serializer[LocalDateTime] {
    private val LocalDateTimeClass = classOf[LocalDateTime]

    def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), LocalDateTime] = {
      case (TypeInfo(LocalDateTimeClass, _), json) => json match {
        case JString(dt) =>  LocalDateTime.parse(dt)
      }
    }

    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case x: LocalDateTime => JString(x.toString)
    }
  }

  implicit val serialization = native.Serialization
  implicit val formats = DefaultFormats + new LocalDateTimeSerializer
  val jsonMarshaller = Marshaller.stringMarshaller(MediaTypes.`application/json`)

  implicit def formatDate(d:LocalDateTime):String = d.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

  implicit def songToJsonMarshaller[T <: AnyRef]: ToEntityMarshaller[T]= {
    val composition= jsonMarshaller.compose(serialization.writePretty[T])
    composition
  }
}

class RestApi (implicit val system:ActorSystem) extends SongToJsonMarshalling{
  implicit val materializer = ActorMaterializer()
  implicit val timeout = Timeout(10 seconds)
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher
  val songRepositoryActor = system.actorSelection("/user/songRepositoryActor")
  val feedPollingActor = system.actorSelection("/user/feedPollingActor")

  val feedRoot = ConfigFactory.load.getString("rtbffeedparser.api.root")
  private var serverBinding:Future[Http.ServerBinding]=
    Future.failed(new IllegalStateException("Server not started"))

  val route = {
    pathPrefix(feedRoot) {
      path("songs") {
        get {
          val fullList = (songRepositoryActor ? GetFullList).mapTo[FullList]
          complete (fullList.map(_.songs))
        }
      } ~ path("refresh_now") {
       post {
         feedPollingActor ! PollNow
         complete(StatusCodes.OK,"Refresh requested")
       }
      }
    } ~ path("ping") {
      get {
        complete {
          val f: WithCharset = ContentTypes.`text/html(UTF-8)`
          HttpEntity(f, "coucou gamin !")
        }
      }
    }
  }


  def start = {
    serverBinding = Http().bindAndHandle(route,"localhost",8080)
  }

  def stop = {
    serverBinding.flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}
