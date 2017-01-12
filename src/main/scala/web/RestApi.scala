package web

import songrepository.SongRepositoryActor.{FullList, GetFullList}

import scala.concurrent.duration._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.Http
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.json4s.{DefaultFormats, Formats, Serializer, TypeInfo, native}
import org.json4s.JsonAST.{JString, JValue}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import domain.Song
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
  * and {@link http://stackoverflow.com/questions/31626759/how-to-write-a-custom-serializer-for-java-8-localdatetime}
  * for LocalDateTime serialization
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
  implicit private val materializer = ActorMaterializer()
  implicit private val timeout = Timeout(10 seconds)
  // needed for the future flatMap/onComplete in the end
  implicit private  val executionContext = system.dispatcher
  private val songRepositoryActor = system.actorSelection("/user/songRepositoryActor")
  private val feedPollingActor = system.actorSelection("/user/feedPollingActor")

  private val feedRoot = ConfigFactory.load.getString("rtbffeedparser.api.root")
  private var serverBinding:Future[Http.ServerBinding]=
    Future.failed(new IllegalStateException("Server not started"))

  private val routes = {
    pathPrefix(feedRoot) {
      pathPrefix("songs") {
        pathEnd {
          get {
            val fullList = (songRepositoryActor ? GetFullList).mapTo[FullList]
            complete(fullList.map(_.songs))
          }
        }/* ~ path("subscribe"){

        }*/
      } ~ path("refresh_now") {
       post {
         feedPollingActor ! PollNow
         complete(StatusCodes.OK,"Refresh requested")
       }
      }
    } ~ pathPrefix("hello") {
      pathEnd {
        get {
          complete {
            val f: WithCharset = MediaTypes.`text/plain`.toContentType(HttpCharsets.`UTF-8`)
            HttpEntity(f, "coucou gamin !")
          }
        }
      }
    }
  }

  val test = {
    Source.queue(100,OverflowStrategy.backpressure).to(Sink.foreach(println))
  }

  def start = {
    serverBinding = Http().bindAndHandle(routes,"localhost",8080)
  }

  def stop = {
    serverBinding.flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}
