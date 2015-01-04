/*
package as.sparkanta.actor.device

import akka.actor.{ ActorLogging, Actor, Props }
import as.akka.broadcaster.Broadcaster
import as.ama.addon.lifecycle.ShutdownSystem
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.ama.config.AmaConfig

object DeviceMessageSender {
  lazy final val maximumQueuedSendDataMessages = 50
}

class DeviceMessageSender(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import DeviceMessageSender._

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new DeviceMessageSenderClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${getClass.getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: Socket.ListeningStarted => listeningStarted(a.request1.message.connectionInfo.remote.id)
    case message                    => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def listeningStarted(id: Long): Unit = {
    val props = Props(new DeviceMessageSenderWorker(id, amaConfig.broadcaster, self, maximumQueuedSendDataMessages))
    context.actorOf(props, name = classOf[DeviceMessageSenderWorker].getSimpleName + "-" + id)
  }
}
*/ 