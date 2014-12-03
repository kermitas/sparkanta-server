package as.sparkanta.ama.actor.tcp

import akka.actor.{ Props, Actor, ActorLogging }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import akka.io.{ IO, Tcp }
import Tcp._
import java.net.InetSocketAddress

class TcpServer(amaConfig: AmaConfig, config: TcpServerConfig) extends Actor with ActorLogging {

  def this(amaConfig: AmaConfig) = this(amaConfig, TcpServerConfig.fromTopKey(amaConfig.config))

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
    case Connected(remoteAddress, localAddress) => incomingConnection(remoteAddress, localAddress)
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

  protected def incomingConnection(remoteAddress: InetSocketAddress, localAddress: InetSocketAddress): Unit = {
    log.info(s"New incoming connection form $remoteAddress (to $localAddress).")
    val props = Props(new TcpConnectionHandler(amaConfig, remoteAddress, localAddress))
    val tcpConnectionHandler = context.actorOf(props)
    sender() ! Register(tcpConnectionHandler)
  }
}