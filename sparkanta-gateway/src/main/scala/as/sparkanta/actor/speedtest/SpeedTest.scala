package as.sparkanta.actor.speedtest

import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import scala.util.{ Try, Success, Failure }
import akka.actor.{ ActorLogging, ActorRef, Actor, Props, OneForOneStrategy, SupervisorStrategy }
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message }

object SpeedTest {
  class StartSpeedTest(val id: Long, val speedTestTimeInMs: Long) extends IncomingReplyableMessage

  abstract class SpeedTestResult(val pingPongsCount: Try[Long], startSpeedTest: StartSpeedTest, startSpeedTestSender: ActorRef) extends OutgoingReplyOn1Message(startSpeedTest, startSpeedTestSender)
  class SpeedTestSuccessResult(pingPongsCount: Long, startSpeedTest: StartSpeedTest, startSpeedTestSender: ActorRef) extends SpeedTestResult(Success(pingPongsCount), startSpeedTest, startSpeedTestSender)
  class SpeedTestErrorResult(exception: Exception, startSpeedTest: StartSpeedTest, startSpeedTestSender: ActorRef) extends SpeedTestResult(Failure(exception), startSpeedTest, startSpeedTestSender)
}

class SpeedTest(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import SpeedTest._

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new SpeedTestClassifier(amaConfig.broadcaster))
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {
    case a: StartSpeedTest => startSpeedTest(a, sender)
    case message           => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def startSpeedTest(startSpeedTest: StartSpeedTest, startSpeedTestSender: ActorRef): Unit = {
    val props = Props(new SpeedTestWorker(startSpeedTest, startSpeedTestSender, amaConfig.broadcaster, self))
    context.actorOf(props)
  }
}
