package as.sparkanta.ama.actor.pongreplier

import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.device.message.{ Ping, Pong }
import as.sparkanta.server.message.MessageToDevice
import as.akka.broadcaster.Broadcaster

class PongReplier(amaConfig: AmaConfig) extends Actor with ActorLogging {

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    try {
      // notifying broadcaster to register us with given classifier
      amaConfig.broadcaster ! new Broadcaster.Register(self, new PongReplierClassifier)

      amaConfig.sendInitializationResult()
    } catch {
      case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
    }
  }

  override def receive = {
    case runtimeId: Long => {
      log.debug(s"Received ${classOf[Ping].getSimpleName} from $runtimeId, replying with ${classOf[Pong].getSimpleName}.")
      amaConfig.broadcaster ! new MessageToDevice(runtimeId, new Pong)
    }

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }
}
