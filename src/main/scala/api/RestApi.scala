package api

import java.time.{LocalDateTime, ZoneOffset}

import actors.BroadCastActor.Subscribe
import actors.FeedPollingActor.PollNow
import actors.SongRepositoryActor.{FullList, GetFullList, NewSong, ResetRepository}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Origin`
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.{JString, JValue}
import org.json4s.{DefaultFormats, Formats, Serializer, TypeInfo, native}

import scala.concurrent.Future
import scala.concurrent.duration._


/**
  * Created by alexandregenon on 08/01/17.
  * Provide the rest api for the clients
  * Also support websocks
  * Why not in specific actors ? Well, it's a front facing stuff thus it makes sense to not make it a
  * mobile thing.
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
        case JString(dt) => LocalDateTime.parse(dt)
      }
    }

    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case x: LocalDateTime => JString(x.toEpochSecond(ZoneOffset.UTC).toString)
    }
  }

  implicit val serialization = native.Serialization
  implicit val formats = DefaultFormats + new LocalDateTimeSerializer
  val jsonMarshaller = Marshaller.stringMarshaller(MediaTypes.`application/json`)

  //implicit def formatDate(d: LocalDateTime): String = d.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

  implicit def songToJsonMarshaller[T <: AnyRef]: ToEntityMarshaller[T] = {
    val composition = jsonMarshaller.compose(serialization.writePretty[T])
    composition
  }
}

class RestApi(implicit val system: ActorSystem) extends SongToJsonMarshalling {
  implicit private val materializer = ActorMaterializer()
  implicit private val timeout = Timeout(10 seconds)

  // needed for the future flatMap/onComplete in the end
  implicit private val executionContext = system.dispatcher

  /**
    * REST Server details the server
    **/
  private val feedRoot = ConfigFactory.load.getString("rtbffeedparser.api.root")
  private var serverBinding: Future[Http.ServerBinding] =
    Future.failed(new IllegalStateException("Server not started"))

  /**
    * Actors in charge of handling messages
    **/
  private val songRepositoryActor = system.actorSelection("/user/songRepositoryActor")
  private val feedPollingActor = system.actorSelection("/user/feedPollingActor")
  private val broadCastActor = system.actorSelection("/user/broadCastActor")

  /**
    * Flow in charge of the web socket
    */
  lazy val wsSubscribeFlowDef = {
    //relay incoming messages to broadCastActor
    val incomingMessages = Sink.foreach[Message](message => broadCastActor ! message)

    // the actor created here will subscribe to broadCastActor
    val outgoingMessages = Source.actorRef[NewSong](10, OverflowStrategy.dropHead).map[Message] {
      case n: NewSong => {
        val songAsJson = serialization.writePretty(n.song)
        TextMessage(songAsJson)
      }
      case other => TextMessage(s"What the ???, un recognized message received $other")
    }

    val keepAlive = {
      val keepAliveMsg = TextMessage("""{"info_type":"keep-alive"}""")
      Source.tick(30 seconds, 30 seconds, keepAliveMsg)
    }

    Flow.fromSinkAndSourceMat(
      incomingMessages,
      outgoingMessages.merge(keepAlive)
    )(Keep.both).mapMaterializedValue { case (future, actor) =>
      broadCastActor ! Subscribe(actor)
      // Do nothing for the moment with the future from the Sink...
      actor
    }
  }

  /**
    * Flow in charge of the websocket
    * => Version using the dynamic stream handling with Broadcast hub
    **/



  lazy val wsSubscribeFlowDef2 = {
    //relay incoming messages to broadCastActor
    val incomingMessages = Sink.foreach[Message](message => broadCastActor ! message)


    // the actor created here will subscribe to broadCastActor
    val outgoingMessages = {
      val keepAlive = {
        val keepAliveMsg = TextMessage("""{"info_type":"keep-alive"}""")
        Source.tick(30 seconds, 30 seconds, keepAliveMsg)
      }
      val broadcast = Source.actorRef[NewSong](16, OverflowStrategy.dropHead)
        .map[Message] {
        case n: NewSong => TextMessage(serialization.writePretty(n.song))
        case other => TextMessage(s"What the ???, un recognized message received $other")
      }
      broadcast.merge(keepAlive)
    }.mapMaterializedValue {
      case actor =>
        broadCastActor ! Subscribe(actor)
        actor
    }

    val broadcastOutgoingMessages =
      outgoingMessages.runWith(BroadcastHub.sink[Message](bufferSize = 16))

    Flow.fromSinkAndSource(
        incomingMessages,
        broadcastOutgoingMessages
      )
  }


  /**
    * API definition
    */
  private val routes = respondWithDefaultHeader(`Access-Control-Allow-Origin`.*) {
    pathPrefix(feedRoot) {
      pathPrefix("songs") {
        pathEnd {
          get {
            val fullList = (songRepositoryActor ? GetFullList).mapTo[FullList]
            complete(fullList.map(_.songs))
          }
        } ~ path("subscribe") {
          handleWebSocketMessages(wsSubscribeFlowDef2)
        }
      } ~ path("refresh_now") {
        post {
          feedPollingActor ! PollNow
          complete(StatusCodes.OK, "Refresh requested")
        }
      } ~ path("reset_past_songs") {
        post {
          songRepositoryActor ! ResetRepository
          complete(StatusCodes.OK, "List of past songs reset")
        }
      }
    } ~ pathPrefix("hello") {
      pathEnd {
        get {
          complete {
            broadCastActor ! "ping"
            val f: WithCharset = MediaTypes.`text/plain`.toContentType(HttpCharsets.`UTF-8`)
            HttpEntity(f, "coucou gamin !")
          }
        }
      }
    }
  }

  /*
   *  Start the API
   */
  def start = {
    serverBinding = Http().bindAndHandle(routes, "localhost", 8080)
  }

  def stop = {
    serverBinding.flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}
