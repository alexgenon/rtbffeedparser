package rtbfjsonprotocol
import domain.Song
import java.time.{LocalDateTime, ZoneOffset}

import scala.util.Try

/**
  * Created by alexandregenon on 07/01/17.
  */
package object RtbfJsonProtocol {
  import org.json4s._
  import org.json4s.native.JsonMethods._
  private implicit val formats = DefaultFormats

  private lazy val epochTime = LocalDateTime.ofEpochSecond(0,0,ZoneOffset.UTC)

  private def extractStringValue(jsonLine: Option[List[(String,JValue)]]):Option[String] =
    jsonLine.map(list=>list.head._2).map(jval => jval.extract[String])

  private def timeFromStr(timeS: String) =
    LocalDateTime.ofEpochSecond(timeS.toLong,0,ZoneOffset.ofHours(1))


  def parseFeed(feed:String): Try[List[Song]] = Try {
    val parsed = parse(feed) \ "results"
    val mergedFeed = (JArray(List(parsed \ "now")) ++ (parsed \ "previous"))
      .asInstanceOf[JArray].arr

    val songsList = for{
      JObject(song) <- mergedFeed
    } yield {
      val mapped = song.groupBy[String](_._1)
        val artist = extractStringValue(
          mapped.get("artistName").orElse(mapped.get("serviceName"))
        ).getOrElse("")
        val songName = extractStringValue(
          mapped.get("name").orElse(mapped.get("programmeName"))
        ).getOrElse("")
        val imageURL = extractStringValue(mapped.get("imageUrl")).getOrElse("")
        val startTime =  extractStringValue(mapped.get("startTime")).map(timeFromStr).get
        val endTime =  extractStringValue(mapped.get("stopTime")).map(timeFromStr).get
        Song(artist,songName,startTime,endTime,imageURL)
    }
    songsList
  }
}
