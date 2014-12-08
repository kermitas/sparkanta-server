package as.sparkanta.ama.actor.disconnectall

import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.akka.broadcaster.Broadcaster
import as.sparkanta.server.message.{ DisconnectAllDevices => DisconnectAllDevicesMessage, MessageToDevice }
import as.sparkanta.gateway.message.{ NewIncomingConnection, ConnectionClosed }
import as.sparkanta.device.message.Disconnect
import scala.collection.mutable.ListBuffer

class DisconnectAllDevices(amaConfig: AmaConfig) extends Actor with ActorLogging {

  protected val currentDevices = ListBuffer[Long]()

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    try {
      // notifying broadcaster to register us with given classifier
      amaConfig.broadcaster ! new Broadcaster.Register(self, new DisconnectAllDevicesClassifier)

      amaConfig.sendInitializationResult()
    } catch {
      case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
    }
  }

  override def receive = {
    case nic: NewIncomingConnection => if (!currentDevices.contains(nic.runtimeId)) currentDevices += nic.runtimeId

    case cc: ConnectionClosed       => currentDevices -= cc.runtimeId

    case dad: DisconnectAllDevicesMessage => {
      val disconnect = new Disconnect(dad.delayBeforeNextConnectionInSeconds)
      currentDevices.foreach(amaConfig.broadcaster ! new MessageToDevice(_, disconnect))
    }

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }
}
