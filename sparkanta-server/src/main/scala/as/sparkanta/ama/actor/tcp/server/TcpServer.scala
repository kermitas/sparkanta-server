package as.sparkanta.ama.actor.tcp.server

import akka.actor.{ SupervisorStrategy, OneForOneStrategy, Props, Actor, ActorRef, ActorLogging }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.actor.tcp.connection.TcpConnectionHandler
import as.sparkanta.ama.config.AmaConfig
import akka.io.{ IO, Tcp }
import Tcp._
import java.net.InetSocketAddress

object TcpServer {
  sealed trait Message extends Serializable
  sealed trait OutgoingMessage extends Message
  class NewIncomingConnection(val remoteAddress: InetSocketAddress, val localAddress: InetSocketAddress, val tcpConnectionHandlerActor: ActorRef) extends OutgoingMessage
}

class TcpServer(amaConfig: AmaConfig, config: TcpServerConfig) extends Actor with ActorLogging {

  import TcpServer._

  def this(amaConfig: AmaConfig) = this(amaConfig, TcpServerConfig.fromTopKey(amaConfig.config))

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart() {
    try {
      // notifying broadcaster to register us with given classifier
      amaConfig.broadcaster ! new Broadcaster.Register(self, new TcpServerClassifier)

      import context.system

      IO(Tcp) ! Bind(self, new InetSocketAddress(config.localBindHost, config.localBindPortNumber))

    } catch {
      case e: Exception => amaConfig.sendInitializationResult(new Exception("Problem while installing sample actor.", e))
    }
  }

  override def receive = {
    case _: Bound                               => boundSuccess
    case CommandFailed(_: Bind)                 => boundFailed
    case Connected(remoteAddress, localAddress) => newIncomingConnection(remoteAddress, localAddress)
    case message                                => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def boundSuccess: Unit = {
    log.info(s"Successfully bound to ${config.localBindHost}:${config.localBindPortNumber}.")
    amaConfig.sendInitializationResult()
  }

  protected def boundFailed: Unit = {
    val message = s"Could not bind to ${config.localBindHost}:${config.localBindPortNumber}."
    log.error(message)
    amaConfig.sendInitializationResult(new Exception(message))
  }

  protected def newIncomingConnection(remoteAddress: InetSocketAddress, localAddress: InetSocketAddress): Unit = {
    log.info(s"New incoming connection form $remoteAddress (to $localAddress).")
    val props = Props(new TcpConnectionHandler(amaConfig, remoteAddress, localAddress))
    val tcpConnectionHandler = context.actorOf(props)
    amaConfig.broadcaster ! new NewIncomingConnection(remoteAddress, localAddress, tcpConnectionHandler)
    sender() ! Register(tcpConnectionHandler)
  }
}