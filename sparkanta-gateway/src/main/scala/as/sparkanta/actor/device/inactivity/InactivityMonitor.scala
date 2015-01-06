package as.sparkanta.actor.device.inactivity

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ Cancellable, ActorRef, ActorLogging, Actor, ActorRefFactory, Props }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.device.message.todevice.Ping
import as.sparkanta.gateway.{ NoAck, Device }

object InactivityMonitor {
  lazy final val ping = new Ping

  object WarningTimeout extends Serializable
  object InactivityTimeout extends Serializable

  def startActor(actorRefFactory: ActorRefFactory, id: Long, broadcaster: ActorRef, warningTimeAfterMs: Int, inactivityTimeAfterMs: Int): ActorRef = {
    val props = Props(new InactivityMonitor(id, broadcaster, warningTimeAfterMs, inactivityTimeAfterMs))
    val actor = actorRefFactory.actorOf(props, name = classOf[InactivityMonitor].getSimpleName + "-" + id)
    broadcaster ! new Broadcaster.Register(actor, new InactivityMonitorClassifier(id))
    actor
  }
}

class InactivityMonitor(id: Long, broadcaster: ActorRef, warningTimeAfterMs: Int, inactivityTimeAfterMs: Int) extends Actor with ActorLogging {

  import InactivityMonitor._
  import context.dispatcher

  protected var warningTimeout: Cancellable = _
  protected var inactivityTimeout: Cancellable = _

  setTimers

  override def postStop(): Unit = cancelTimers

  override def receive = {
    case _: Device.NewMessage               => resetTimers
    case a: Device.SendMessageSuccessResult => // do nothing
    case a: Device.SendMessageErrorResult   => stop(new Exception(s"Problem while sending $ping to device of remote address id $id.", a.exception))
    case WarningTimeout => {
      log.debug(s"Device of remote address id $id is inactive for more than $warningTimeAfterMs milliseconds, sending ${ping.getClass.getSimpleName}.")
      broadcaster ! new Device.SendMessage(id, ping, NoAck)
    }
    case InactivityTimeout => stop(new Exception(s"Device of remote address id $id exceeded inactivity timeout ($inactivityTimeAfterMs milliseconds)."))
    case message           => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def setTimers: Unit = {
    warningTimeout = context.system.scheduler.scheduleOnce(warningTimeAfterMs millis, self, WarningTimeout)
    inactivityTimeout = context.system.scheduler.scheduleOnce(inactivityTimeAfterMs millis, self, InactivityTimeout)
  }

  protected def cancelTimers: Unit = {
    warningTimeout.cancel
    inactivityTimeout.cancel
  }

  protected def resetTimers: Unit = {
    cancelTimers
    setTimers
  }

  protected def stop(exception: Exception): Unit = {
    cancelTimers

    val exception = new Exception(s"Device of remote address id $id exceeded inactivity timeout ($inactivityTimeAfterMs milliseconds).")
    broadcaster ! new Device.StopDevice(id, exception)
    context.stop(self)
  }
}