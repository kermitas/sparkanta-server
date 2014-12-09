package as.sparkanta.ama.actor.tcp.server

import akka.actor.{ SupervisorStrategy, OneForOneStrategy, Props, Actor, ActorRef, ActorLogging }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.actor.tcp.connection.TcpConnectionHandler
import as.sparkanta.ama.config.AmaConfig
import akka.io.{ IO, Tcp }
import java.net.InetSocketAddress
import akka.util.ActorNameGenerator
import as.sparkanta.gateway.message.NewIncomingConnection
import as.sparkanta.ama.actor.restforwarder.RestForwarder

class TcpServer(
  amaConfig:                               AmaConfig,
  config:                                  TcpServerConfig,
  tcpConnectionHandlerActorNamesGenerator: ActorNameGenerator,
  restForwarderActorNamesGenerator:        ActorNameGenerator
) extends Actor with ActorLogging {

  def this(amaConfig: AmaConfig) = this(
    amaConfig, TcpServerConfig.fromTopKey(amaConfig.config),
    new ActorNameGenerator(classOf[TcpConnectionHandler].getSimpleName + "-%s"),
    new ActorNameGenerator(classOf[RestForwarder].getSimpleName + "-%s")
  )

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

      IO(Tcp) ! Tcp.Bind(self, new InetSocketAddress(config.localBindHost, config.localBindPortNumber))

    } catch {
      case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
    }
  }

  override def receive = {
    case _: Tcp.Bound                               => boundSuccess
    case Tcp.CommandFailed(_: Tcp.Bind)             => boundFailed
    case Tcp.Connected(remoteAddress, localAddress) => newIncomingConnection(remoteAddress, localAddress, sender())

    // TODO receive actor's death notification

    case message                                    => log.warning(s"Unhandled $message send by ${sender()}")
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

  protected def newIncomingConnection(remoteAddress: InetSocketAddress, localAddress: InetSocketAddress, tcpActor: ActorRef): Unit =
    newIncomingConnection(remoteAddress.getHostString, remoteAddress.getPort, localAddress.getHostString, localAddress.getPort, tcpActor)

  protected def newIncomingConnection(remoteIp: String, remotePort: Int, localIp: String, localPort: Int, tcpActor: ActorRef): Unit = {
    val runtimeId = tcpConnectionHandlerActorNamesGenerator.numberThatWillBeUsedToGenerateNextName
    log.info(s"New incoming connection form $remoteIp:$remotePort (to $localIp:$localPort), assigning runtime id $runtimeId.")
    val tcpConnectionHandler = startTcpConnectionHandlerActor(remoteIp, remotePort, localIp, localPort, tcpActor, runtimeId)

    val fakeRestIp = "" // TODO, passed via constructor
    val fakeRestPort = -1 // TODO, passed via constructor
    val restForwarder = startRestForwarder(localIp, localPort, fakeRestIp, fakeRestPort)

    amaConfig.broadcaster ! new NewIncomingConnection(remoteIp, remotePort, localIp, localPort, runtimeId)
    tcpActor ! Tcp.Register(tcpConnectionHandler)

    // TODO watch for death of tcpConnectionHandler
    // TODO watch for death of restForwarder
  }

  protected def startTcpConnectionHandlerActor(remoteIp: String, remotePort: Int, localIp: String, localPort: Int, tcpActor: ActorRef, runtimeId: Long): ActorRef = {
    val props = Props(new TcpConnectionHandler(amaConfig, remoteIp, remotePort, localIp, localPort, tcpActor, runtimeId))
    context.actorOf(props, name = tcpConnectionHandlerActorNamesGenerator.nextName)
  }

  protected def startRestForwarder(localIp: String, localPort: Int, restIp: String, restPort: Int): ActorRef = {
    val props = Props(new RestForwarder(amaConfig, localIp, localPort, restIp, restPort))
    context.actorOf(props, name = restForwarderActorNamesGenerator.nextName)
  }
}