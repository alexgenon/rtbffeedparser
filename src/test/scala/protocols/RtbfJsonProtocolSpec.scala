package protocols

import RtbfJsonProtocol._
import org.scalatest.{FlatSpec, Matchers}
/**
  * Created by alexandregenon on 07/01/17.
  */
class RtbfJsonProtocolSpec extends FlatSpec with Matchers{
  lazy val jsonFile = scala.io.Source.fromURL(getClass.getResource("/sampleRtbfJson")).mkString
  lazy val liveJson = scala.io.Source.fromURL("http://np.maradio.be/qp/v3/events?rpId=11").mkString

  "RtbfJsonProtocol" must "Correctly parse a locally saved Json" in {
    val result = parseFeed(jsonFile)
    result.recover{ case error => println(error); List()}
    result.isSuccess should be (true)
    val list = result.get
    list.size should be (6)
    list(1).artist.toLowerCase should include ("lazer")
  }

  it must "Correctly parse a live feed" in {
    val result = parseFeed(liveJson)
    result.isSuccess should be (true)
    val list = result.get
    list.foreach(println)
    list.size should be > 0
  }

}
