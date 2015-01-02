package as.sparkanta.actor2.speedtest

import scala.util.{ Try, Success, Failure }
import akka.actor.ActorRef
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message }

object SpeedTest {
  class StartSpeedTest(val id: Long, val speedTestTimeInMs: Long) extends IncomingReplyableMessage

  abstract class SpeedTestResult(val pingPongsCount: Try[Long], startSpeedTest: StartSpeedTest, startSpeedTestSender: ActorRef) extends OutgoingReplyOn1Message(startSpeedTest, startSpeedTestSender)
  class SpeedTestSuccessResult(pingPongsCount: Long, startSpeedTest: StartSpeedTest, startSpeedTestSender: ActorRef) extends SpeedTestResult(Success(pingPongsCount), startSpeedTest, startSpeedTestSender)
  class SpeedTestErrorResult(exception: Exception, startSpeedTest: StartSpeedTest, startSpeedTestSender: ActorRef) extends SpeedTestResult(Failure(exception), startSpeedTest, startSpeedTestSender)
}

class SpeedTest {

}
