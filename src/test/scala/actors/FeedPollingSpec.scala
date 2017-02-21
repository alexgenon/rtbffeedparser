package actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestFSMRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

/**
  * Created by alexandregenon on 07/01/17.
  */
class FeedPollingSpec extends TestKit(ActorSystem("TestActorSystem"))
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  import FeedPollingActor._
  val reconciliationProbe = TestProbe()
  val feedPollingActor = TestFSMRef(new FeedPollingActor(reconciliationProbe.ref))
  val feedPollingActorType:TestActorRef[FeedPollingActor] = feedPollingActor

  override def beforeAll(): Unit = feedPollingActor ! UpdatePollInterval (3 seconds)

  "A FeedPollingActor" must "not accept poll request until fully configured" in {
    assert(feedPollingActor.stateName == Configuring)
    feedPollingActor ! PollNow
    assert(feedPollingActor.stateName == Configuring)
    expectMsgClass(classOf[Error])
  }

  it must "not accept malformed URLs" in {
    feedPollingActor ! UpdateURL("","none")
    expectMsgClass(classOf[Error])
  }

  it must "properly fetch a default URL on demand" in {
    feedPollingActor ! UpdateURL("http://np.maradio.be/qp/v3/events","11")
    assert(feedPollingActor.isTimerActive(FeedPollingActor.POLLTIMERNAME))
    //assert(feedPollingActor.stateName == Idle)
    feedPollingActor ! PollNow
  }

  it must "fetch periodically" in {
    Thread.sleep(4000)
  }


  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)
}
