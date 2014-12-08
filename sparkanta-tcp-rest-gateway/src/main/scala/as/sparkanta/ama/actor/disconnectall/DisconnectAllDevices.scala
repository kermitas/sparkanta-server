package as.sparkanta.ama.actor.disconnectall

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.akka.broadcaster.Broadcaster
import as.sparkanta.gateway.message.{ GetCurrentDevices, CurrentDevices }
import as.sparkanta.server.message.{ DisconnectAllDevices => DisconnectAllDevicesMessage, MessageToDevice }
import as.sparkanta.device.message.Disconnect
import akka.pattern.ask
import scala.util.{ Success, Failure }

object DisconnectAllDevices {
  lazy final val queryingAllDevicesTimeoutInSeconds = 2
}

class DisconnectAllDevices(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import DisconnectAllDevices._

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

    case dad: DisconnectAllDevicesMessage => disconnectAll(dad.delayBeforeNextConnectionInSeconds)

    case message                          => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def disconnectAll(delayBeforeNextConnectionInSeconds: Int): Unit = {
    val disconnect = new Disconnect(delayBeforeNextConnectionInSeconds)

    import context.dispatcher

    ask(amaConfig.broadcaster, new GetCurrentDevices)(queryingAllDevicesTimeoutInSeconds seconds).mapTo[CurrentDevices].map(_.devices).onComplete {
      case Success(devices) => devices.foreach(r => amaConfig.broadcaster ! new MessageToDevice(r.runtimeId, disconnect))
      case Failure(t)       => log.error(t, "Could not query all devices that are currently in system.")
    }
  }
}
