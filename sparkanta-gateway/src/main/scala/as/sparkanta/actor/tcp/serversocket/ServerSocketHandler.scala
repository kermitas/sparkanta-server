/*
package as.sparkanta.actor.tcp.serversocket

import akka.actor.{ SupervisorStrategy, OneForOneStrategy, Props, Actor, ActorRef, ActorLogging, Terminated }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.actor.tcp.socket.SocketHandler
import as.sparkanta.ama.config.AmaConfig
import akka.io.{ IO, Tcp }
import java.net.InetSocketAddress
import as.sparkanta.gateway.message.NewIncomingConnection
//import as.sparkanta.actor.restforwarder.RestForwarder
import as.sparkanta.server.message.{ StopListeningAt, ListenAt, ListenAtSuccessResult, ListenAtErrorResult }
import scala.net.IdentifiedInetSocketAddress
//import as.sparkanta.gateway.NetworkDeviceInfo
import scala.collection.mutable.Set

class ServerSocketHandler(
  amaConfig:                  AmaConfig,
  listenAt:                   ListenAt,
  var listenAtResultListener: ActorRef,
  staticIdsCurrentlyOnline:   Set[Long]
) extends Actor with ActorLogging {

  protected var incomingConnectionsNumerator: Long = 0

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def receive = {
    case Tcp.Connected(remoteAddress, _) => newIncomingConnection(remoteAddress, sender())
    case true                            => bind
    case _: Tcp.Bound                    => boundSuccess
    case Tcp.CommandFailed(_: Tcp.Bind)  => boundFailed
    //case Terminated(deadWatchedActor)    => restForwarderIsDead
    case sla: StopListeningAt            => stopListeningAt(sla)
    case message                         => log.warning(s"Unhandled $message send by ${sender()}")
  }

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new ServerSocketHandlerClassifier(listenAt.listenAddress.id))
  }

  protected def bind: Unit = {
    import context.system
    log.debug(s"Trying to bind to ${listenAt.listenAddress}.")
    IO(Tcp) ! Tcp.Bind(self, listenAt.listenAddress)
  }

  protected def boundSuccess: Unit = {
    log.info(s"Successfully bound to ${listenAt.listenAddress}.")

    /*
    val restForwarder = startRestForwarder
    context.watch(restForwarder)
    */

    listenAtResultListener ! new ListenAtSuccessResult(listenAt)
    listenAtResultListener = null
  }

  protected def boundFailed: Unit = {
    val message = s"Could not bind to ${listenAt.listenAddress}."
    log.error(message)

    listenAtResultListener ! new ListenAtErrorResult(listenAt, new Exception(message))
    context.stop(self)
  }

  protected def newIncomingConnection(remoteAddress: InetSocketAddress, tcpActor: ActorRef): Unit = {

    log.info(s"New incoming connection form $remoteAddress to local ${listenAt.listenAddress}.")

    val socketHandler = startSocketHandlerActor(remoteAddress, tcpActor)

    amaConfig.broadcaster ! new NewIncomingConnection(remoteAddress, listenAt.listenAddress)
    tcpActor ! Tcp.Register(socketHandler)
  }

  protected def startSocketHandlerActor(remoteAddress: InetSocketAddress, tcpActor: ActorRef): ActorRef = {
    val props = Props(new SocketHandler(amaConfig, remoteAddress, listenAt.listenAddress, listenAt.deviceStaticIds, staticIdsCurrentlyOnline, tcpActor))
    val socketHandler = context.actorOf(props, name = classOf[SocketHandler].getSimpleName + "-" + incomingConnectionsNumerator)
    incomingConnectionsNumerator += 1
    socketHandler
  }

  /*
  protected def restForwarderIsDead: Unit = {
    log.warning(s"Stopping because ${classOf[RestForwarder].getSimpleName} died.")
    context.stop(self)
  }

  protected def startRestForwarder: ActorRef = {
    val props = Props(new RestForwarder(amaConfig, listenAt.listenAddress, listenAt.forwardToRestAddress))
    context.actorOf(props, name = classOf[RestForwarder].getSimpleName + "-" + listenAt.listenAddress.id)
  }*/

  protected def stopListeningAt(sla: StopListeningAt): Unit = {
    log.debug(s"Received $sla, stop listening at ${listenAt.listenAddress}.")
    context.stop(self)
  }
}
*/ 