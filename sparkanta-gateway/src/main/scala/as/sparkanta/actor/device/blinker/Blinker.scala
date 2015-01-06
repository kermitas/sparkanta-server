package as.sparkanta.actor.device.blinker

import akka.actor.{ ActorLogging, Actor, Props, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.ama.addon.lifecycle.ShutdownSystem
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.gateway.Device

class Blinker(amaConfig: AmaConfig) extends Actor with ActorLogging {

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new BlinkerClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${getClass.getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: Device.IdentifiedDeviceUp => {
      val props = Props(new BlinkerWorker(a.deviceInfo.connectionInfo.remote.id, amaConfig.broadcaster))
      context.actorOf(props, name = classOf[BlinkerWorker].getSimpleName + "-" + a.deviceInfo.connectionInfo.remote.id)
    }

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }

}