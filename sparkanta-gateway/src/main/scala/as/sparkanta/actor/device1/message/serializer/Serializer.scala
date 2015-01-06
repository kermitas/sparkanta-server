/*
package as.sparkanta.actor.device1.message.serializer

import akka.actor.{ ActorLogging, Actor, Props, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.ama.addon.lifecycle.ShutdownSystem
import as.sparkanta.actor.tcp.serversocket.ServerSocket
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.gateway.Device

object Serializer {
  lazy final val maximumQueuedSendDataMessages = 50 // TODO move to config
}

class Serializer(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import Serializer._

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new SerializerClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${getClass.getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: ServerSocket.NewConnection => newConnection(a)
    case message                       => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def newConnection(newConnectionMessage: ServerSocket.NewConnection): Unit =
    newConnection(newConnectionMessage.connectionInfo.remote.id)

  protected def newConnection(id: Long): Unit = try {
    val props = Props(new SerializerWorker(id, amaConfig.broadcaster, self, maximumQueuedSendDataMessages))
    context.actorOf(props, name = classOf[SerializerWorker].getSimpleName + "-" + id)
  } catch {
    case e: Exception => {
      val exception = new Exception(s"Problem during setup work for device of remote address id ${id}.", e)
      log.error(exception, exception.getMessage)
      amaConfig.broadcaster ! new Device.StopDevice(id, exception)
    }
  }
}
*/ 