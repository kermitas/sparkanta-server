package as.sparkanta.actor.tcp.socket

import akka.actor.{ Actor, Props, ActorLogging, SupervisorStrategy, OneForOneStrategy }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.message.{ ConnectionClosed, NewIncomingConnection }

class SocketManager(amaConfig: AmaConfig) extends Actor with ActorLogging {

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = try {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new SocketManagerClassifier)

    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {
    case nic: NewIncomingConnection => newIncomingConnection(nic)
    case message                    => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def newIncomingConnection(nic: NewIncomingConnection): Unit = try {

    log.debug(s"Creating ${classOf[SocketHandler].getSimpleName} for new incoming connection from ${nic.networkDeviceInfo.remoteAddress} to ${nic.networkDeviceInfo.localAddress}.")

    val props = Props(new SocketHandler(nic.networkDeviceInfo, nic.tcpActor))
    context.actorOf(props, name = classOf[SocketHandler].getSimpleName + "-" + nic.networkDeviceInfo.remoteAddress.id)
  } catch {
    case e: Exception =>
      val message = s"Problem while initializing ${classOf[SocketHandler].getSimpleName}."
      log.error(message, e)

      amaConfig.broadcaster ! new ConnectionClosed(nic.networkDeviceInfo, Some(new Exception(message, e)))
  }
}
