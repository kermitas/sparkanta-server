package as.sparkanta.ama.actor.tcp.serversocket

import akka.actor.{ SupervisorStrategy, OneForOneStrategy, Props, Actor, ActorRef, ActorLogging, Terminated }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.actor.tcp.socket.SocketHandler
import as.sparkanta.ama.config.AmaConfig
import akka.io.{ IO, Tcp }
import java.net.InetSocketAddress
import as.sparkanta.gateway.message.NewIncomingConnection
import as.sparkanta.ama.actor.restforwarder.RestForwarder
import java.util.concurrent.atomic.AtomicLong
import as.sparkanta.server.message.{ StopListeningAt, ListenAt, ListenAtSuccessResult, ListenAtErrorResult }

class ServerSocketHandler(
  amaConfig:                  AmaConfig,
  runtimeIdNumerator:         AtomicLong,
  var listenAt:               ListenAt,
  var listenAtResultListener: ActorRef
) extends Actor with ActorLogging {

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def receive = {
    case true                                       => bound
    case _: Tcp.Bound                               => boundSuccess
    case Tcp.CommandFailed(_: Tcp.Bind)             => boundFailed
    case Tcp.Connected(remoteAddress, localAddress) => newIncomingConnection(remoteAddress, localAddress, sender())
    case Terminated(deadWatchedActor)               => restForwarderIsDead
    case sla: StopListeningAt                       => context.stop(self)
    case message                                    => log.warning(s"Unhandled $message send by ${sender()}")
  }

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new ServerSocketHandlerClassifier(listenAt.listenIp, listenAt.listenPort))
  }

  protected def bound: Unit = {
    import context.system
    log.debug(s"Trying to bind to ${listenAt.listenIp}:${listenAt.listenPort}.")
    IO(Tcp) ! Tcp.Bind(self, new InetSocketAddress(listenAt.listenIp, listenAt.listenPort))
  }

  protected def boundSuccess: Unit = {
    log.info(s"Successfully bound to ${listenAt.listenIp}:${listenAt.listenPort}.")

    val restForwarder = startRestForwarder(listenAt.listenIp, listenAt.listenPort, listenAt.forwardToRestIp, listenAt.forwardToRestPort)
    context.watch(restForwarder)

    listenAtResultListener ! new ListenAtSuccessResult(listenAt)

    listenAt = null
    listenAtResultListener = null
  }

  protected def boundFailed: Unit = {
    val message = s"Could not bind to ${listenAt.listenIp}:${listenAt.listenPort}."
    log.error(message)

    listenAtResultListener ! new ListenAtErrorResult(listenAt, new Exception(message))
    context.stop(self)
  }

  protected def newIncomingConnection(remoteAddress: InetSocketAddress, localAddress: InetSocketAddress, tcpActor: ActorRef): Unit =
    newIncomingConnection(remoteAddress.getHostString, remoteAddress.getPort, localAddress.getHostString, localAddress.getPort, tcpActor)

  protected def newIncomingConnection(remoteIp: String, remotePort: Int, localIp: String, localPort: Int, tcpActor: ActorRef): Unit = {

    val runtimeId = runtimeIdNumerator.getAndIncrement

    log.info(s"New incoming connection form $remoteIp:$remotePort (to $localIp:$localPort), assigning runtime id $runtimeId.")

    val socketHandler = startSocketHandlerActor(remoteIp, remotePort, localIp, localPort, tcpActor, runtimeId)

    amaConfig.broadcaster ! new NewIncomingConnection(remoteIp, remotePort, localIp, localPort, runtimeId)
    tcpActor ! Tcp.Register(socketHandler)
  }

  protected def startSocketHandlerActor(remoteIp: String, remotePort: Int, localIp: String, localPort: Int, tcpActor: ActorRef, runtimeId: Long): ActorRef = {
    val props = Props(new SocketHandler(amaConfig, remoteIp, remotePort, localIp, localPort, tcpActor, runtimeId))
    context.actorOf(props, name = classOf[SocketHandler].getSimpleName + "-" + runtimeId)
  }

  protected def restForwarderIsDead: Unit = {
    log.warning(s"Stopping because ${classOf[RestForwarder].getSimpleName} died.")
    context.stop(self)
  }

  protected def startRestForwarder(localIp: String, localPort: Int, restIp: String, restPort: Int): ActorRef = {
    val props = Props(new RestForwarder(amaConfig, localIp, localPort, restIp, restPort))
    context.actorOf(props, name = classOf[RestForwarder].getSimpleName)
  }
}