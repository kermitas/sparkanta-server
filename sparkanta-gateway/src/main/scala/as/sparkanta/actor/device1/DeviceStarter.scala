package as.sparkanta.actor.device1

import akka.actor.{ ActorLogging, Actor }
import as.akka.broadcaster.Broadcaster
import as.ama.addon.lifecycle.ShutdownSystem
import as.sparkanta.actor.tcp.serversocket.ServerSocket
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.gateway.{ Device => DeviceSpec }
import as.sparkanta.actor.device1.message.serializer.DeviceSerializer

object DeviceStarter {
  lazy final val maximumQueuedSendDataMessages = 50 // TODO move to config
  lazy final val deviceIdentificationTimeoutInMs = 2 * 1000 // TODO move to config
  lazy final val pingPongSpeedTestTimeInMs: Option[Long] = Some(1 * 1000) // TODO move to config
}

class DeviceStarter(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import DeviceStarter._

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new DeviceStarterClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${getClass.getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: ServerSocket.NewConnection      => newConnection(a)
    case a: DeviceSpec.StartErrorResult     => log.error(a.exception, a.exception.getMessage)
    case _: DeviceSpec.StartSuccessResult   => // do nothing
    case _: DeviceSpec.Started              => // do nothing
    case _: DeviceSpec.Stopped              => // do nothing
    case _: DeviceSpec.IdentifiedDeviceUp   => // do nothing
    case _: DeviceSpec.IdentifiedDeviceDown => // do nothing
    case message                            => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def newConnection(newConnectionMessage: ServerSocket.NewConnection): Unit = try {

    DeviceSerializer.startActor(context, newConnectionMessage.connectionInfo.remote.id, amaConfig.broadcaster, self, maximumQueuedSendDataMessages)

    amaConfig.broadcaster ! new DeviceSpec.Start(newConnectionMessage.connectionInfo, newConnectionMessage.akkaSocketTcpActor, maximumQueuedSendDataMessages, deviceIdentificationTimeoutInMs, pingPongSpeedTestTimeInMs)

  } catch {
    case e: Exception => {
      val exception = new Exception(s"Problem during setup work for device of remote address id ${newConnectionMessage.connectionInfo.remote.id}.", e)
      log.error(exception, exception.getMessage)
      amaConfig.broadcaster ! new DeviceSpec.StopDevice(newConnectionMessage.connectionInfo.remote.id, exception)
    }
  }
}
