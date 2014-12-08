package as.sparkanta.ama.actor.tcp.server

import akka.actor.{ SupervisorStrategy, OneForOneStrategy, Props, Actor, ActorRef, ActorLogging }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.actor.tcp.connection.TcpConnectionHandler
import as.sparkanta.ama.config.AmaConfig
import akka.io.{ IO, Tcp }
import Tcp._
import java.net.InetSocketAddress
import akka.util.ActorNameGenerator
import java.util.concurrent.atomic.AtomicLong
import as.sparkanta.gateway.message.NewIncomingConnection

class TcpServer(amaConfig: AmaConfig, config: TcpServerConfig) extends Actor with ActorLogging {

  def this(amaConfig: AmaConfig) = this(amaConfig, TcpServerConfig.fromTopKey(amaConfig.config))

  protected val tcpConnectionHandlerActorNamesGenerator = new ActorNameGenerator(classOf[TcpConnectionHandler].getSimpleName + "-%s")

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    try {
      // notifying broadcaster to register us with given classifier
      amaConfig.broadcaster ! new Broadcaster.Register(self, new TcpServerClassifier)

      import context.system

      IO(Tcp) ! Bind(self, new InetSocketAddress(config.localBindHost, config.localBindPortNumber))

    } catch {
      case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
    }
  }

  override def receive = {
    case _: Bound                               => boundSuccess
    case CommandFailed(_: Bind)                 => boundFailed
    case Connected(remoteAddress, localAddress) => newIncomingConnection(remoteAddress, localAddress, sender())
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

  protected def newIncomingConnection(remoteAddress: InetSocketAddress, localAddress: InetSocketAddress, tcpActor: ActorRef): Unit = {
    val runtimeId = tcpConnectionHandlerActorNamesGenerator.numberThatWillBeUsedToGenerateNextName
    log.info(s"New incoming connection form $remoteAddress (to $localAddress), assigning runtime id $runtimeId.")
    val tcpConnectionHandler = startTcpConnectionHandlerActor(remoteAddress, localAddress, tcpActor, runtimeId)
    amaConfig.broadcaster ! new NewIncomingConnection(remoteAddress, localAddress, runtimeId)
    tcpActor ! Register(tcpConnectionHandler)
  }

  protected def startTcpConnectionHandlerActor(remoteAddress: InetSocketAddress, localAddress: InetSocketAddress, tcpActor: ActorRef, runtimeId: Long): ActorRef = {
    val props = Props(new TcpConnectionHandler(amaConfig, remoteAddress, localAddress, tcpActor, runtimeId))
    context.actorOf(props, name = tcpConnectionHandlerActorNamesGenerator.nextName)
  }
}