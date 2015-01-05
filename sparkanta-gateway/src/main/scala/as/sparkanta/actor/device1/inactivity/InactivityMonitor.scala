package as.sparkanta.actor.device1.inactivity

import akka.actor.{ ActorLogging, Actor }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.ama.addon.lifecycle.ShutdownSystem
import as.sparkanta.gateway.Device
import as.sparkanta.actor.inactivity.{ InactivityMonitor => InactivityMonitorSpec }
import as.sparkanta.device.message.todevice.Ping
import as.sparkanta.gateway.NoAck

object InactivityMonitor {
  lazy final val warningTimeAfterMs = 2 * 1000 // TODO move to config
  lazy final val inactivityTimeAfterMs = 3 * 1000 // TODO move to config

  lazy final val ping = new Ping
}

class InactivityMonitor(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import InactivityMonitor._

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new InactivityMonitorClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${getClass.getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: Device.NewMessage => newMessage(a)
    case a: Device.IdentifiedDeviceUp => identifiedDeviceUp(a)
    case a: Device.IdentifiedDeviceDown => identifiedDeviceDown(a)
    case a: InactivityMonitorSpec.InactivityWarning => inactivityWarning(a)
    case a: InactivityMonitorSpec.InactivityDetected => inactivityDetected(a)
    case a: InactivityMonitorSpec.StartInactivityMonitorSuccessResult => // do nothing
    case a: InactivityMonitorSpec.StartInactivityMonitorErrorResult => startInactivityMonitorError(a)

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def identifiedDeviceUp(identifiedDeviceUpMessage: Device.IdentifiedDeviceUp): Unit =
    identifiedDeviceUp(identifiedDeviceUpMessage.deviceInfo.connectionInfo.remote.id)

  protected def identifiedDeviceUp(id: Long): Unit = {
    amaConfig.broadcaster ! new InactivityMonitorSpec.StartInactivityMonitor(id, warningTimeAfterMs, inactivityTimeAfterMs)
  }

  protected def startInactivityMonitorError(startInactivityMonitorErrorResult: InactivityMonitorSpec.StartInactivityMonitorErrorResult): Unit =
    startInactivityMonitorError(startInactivityMonitorErrorResult.request1.message.id, startInactivityMonitorErrorResult.exception)

  protected def startInactivityMonitorError(id: Long, exception: Exception) {
    val e = new Exception("Problem while starting inactivity monitor.", exception)
    amaConfig.broadcaster ! new Device.StopDevice(id, e)
  }

  protected def identifiedDeviceDown(identifiedDeviceDownMessage: Device.IdentifiedDeviceDown): Unit =
    identifiedDeviceDown(identifiedDeviceDownMessage.deviceInfo.connectionInfo.remote.id)

  protected def identifiedDeviceDown(id: Long): Unit = amaConfig.broadcaster ! new InactivityMonitorSpec.StopInactivityMonitor(id)

  protected def inactivityWarning(inactivityWarningMessage: InactivityMonitorSpec.InactivityWarning): Unit =
    inactivityWarning(inactivityWarningMessage.request1.message.id)

  protected def inactivityWarning(id: Long): Unit = amaConfig.broadcaster ! new Device.SendMessage(id, ping, NoAck)

  protected def inactivityDetected(inactivityDetectedMessage: InactivityMonitorSpec.InactivityDetected): Unit =
    inactivityDetected(inactivityDetectedMessage.request1.message.id, inactivityDetectedMessage.inactivityTimeInMs)

  protected def inactivityDetected(id: Long, inactivityTimeInMs: Long): Unit = {
    val exception = new Exception(s"Device of remote address id $id exceeded inactivity timeout ($inactivityTimeAfterMs milliseconds) by ${inactivityTimeInMs - inactivityTimeAfterMs} milliseconds.")
    amaConfig.broadcaster ! new Device.StopDevice(id, exception)
  }

  protected def newMessage(newIncomingMessage: Device.NewMessage): Unit = newMessage(newIncomingMessage.deviceInfo.connectionInfo.remote.id)

  protected def newMessage(id: Long): Unit = amaConfig.broadcaster ! new InactivityMonitorSpec.Active(id)
}
