package as.sparkanta.actor.speedtest

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorLogging, ActorRef, Actor, Cancellable }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.gateway.{ Device, NoAck }
import as.sparkanta.device.message.todevice.Ping
import akka.util.InternalMessage

object SpeedTestWorker {
  lazy final val ping = new Ping

  object SpeedTestTimeout extends InternalMessage
}

class SpeedTestWorker(startSpeedTest: SpeedTest.StartSpeedTest, startSpeedTestSender: ActorRef, broadcaster: ActorRef, speedTestActor: ActorRef) extends Actor with ActorLogging {

  import SpeedTestWorker._
  import context.dispatcher

  protected var pingPongCount = 0L
  protected var timeout: Cancellable = null

  override def preStart(): Unit = {
    broadcaster ! new Broadcaster.Register(self, new SpeedTestWorkerClassifier(startSpeedTest.id))
    timeout = context.system.scheduler.scheduleOnce(startSpeedTest.speedTestTimeInMs millis, self, SpeedTestTimeout)
    sendPing
  }

  override def receive = {
    case a: Device.NewMessage => {
      pingPongCount += 1
      sendPing
    }

    case a: Device.SendMessageErrorResult => {
      timeout.cancel
      val speedTestErrorResult = new SpeedTest.SpeedTestErrorResult(a.exception.get, startSpeedTest, startSpeedTestSender)
      speedTestErrorResult.reply(speedTestActor)
      context.stop(self)
    }

    case SpeedTestTimeout => {
      val speedTestSuccessResult = new SpeedTest.SpeedTestSuccessResult(pingPongCount, startSpeedTest, startSpeedTestSender)
      speedTestSuccessResult.reply(speedTestActor)
      context.stop(self)
    }

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def sendPing: Unit = broadcaster ! new Device.SendMessage(startSpeedTest.id, ping, NoAck)
}
