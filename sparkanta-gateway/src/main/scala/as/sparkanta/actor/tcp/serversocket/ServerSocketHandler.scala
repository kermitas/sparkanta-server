/*
package as.sparkanta.actor.tcp.serversocket

import akka.actor.{ Actor, ActorRef, ActorLogging }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.gateway.NetworkDeviceInfo
import as.sparkanta.ama.config.AmaConfig
import akka.io.{ IO, Tcp }
import java.net.InetSocketAddress
import as.sparkanta.message.{ StopListeningAt, ListenAt, ListenAtSuccessResult, ListenAtErrorResult, NewIncomingConnection, ListeningStopped }
import scala.net.IdentifiedInetSocketAddress
import java.util.concurrent.atomic.AtomicLong

class ServerSocketHandler(
  amaConfig:                        AmaConfig,
  listenAt:                         ListenAt,
  remoteConnectionsUniqueNumerator: AtomicLong
) extends Actor with ActorLogging {

  protected var wasSuccessfullyBound = false

  override def receive = {
    case Tcp.Connected(remoteAddress, _) => newIncomingConnection(remoteAddress, sender())
    case _: Tcp.Bound                    => boundSuccess
    case Tcp.CommandFailed(_: Tcp.Bind)  => boundFailed
    case sla: StopListeningAt            => stopListeningAt(sla)
    case message                         => log.warning(s"Unhandled $message send by ${sender()}")
  }

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new ServerSocketHandlerClassifier(listenAt.listenAddress.id))

    bind
  }

  override def postStop(): Unit = {
    if (wasSuccessfullyBound) amaConfig.broadcaster ! new ListeningStopped(listenAt)
  }

  protected def bind: Unit = try {
    import context.system
    log.debug(s"Trying to bind to ${listenAt.listenAddress}.")
    IO(Tcp) ! Tcp.Bind(self, listenAt.listenAddress)
  } catch {
    case e: Exception => {
      amaConfig.broadcaster ! new ListenAtErrorResult(listenAt, new Exception("Problem while preparing to bind.", e))
      context.stop(self)
    }
  }

  protected def boundSuccess: Unit = {
    log.info(s"Successfully bound to ${listenAt.listenAddress}.")

    amaConfig.broadcaster ! new ListenAtSuccessResult(listenAt)
    wasSuccessfullyBound = true
  }

  protected def boundFailed: Unit = {
    amaConfig.broadcaster ! new ListenAtErrorResult(listenAt, new Exception(s"Could not bind to ${listenAt.listenAddress}."))
    context.stop(self)
  }

  protected def newIncomingConnection(remoteAddr: InetSocketAddress, tcpActor: ActorRef): Unit = try {
    val remoteAddress = new IdentifiedInetSocketAddress(remoteConnectionsUniqueNumerator.getAndIncrement, remoteAddr.getHostString, remoteAddr.getPort)
    newIncomingConnection(remoteAddress, tcpActor)
  } catch {
    case e: Exception => log.error(s"Problem while accepting new incoming connection from $remoteAddr to ${listenAt.listenAddress}.", e)
  }

  protected def newIncomingConnection(remoteAddress: IdentifiedInetSocketAddress, tcpActor: ActorRef): Unit = {
    log.info(s"New incoming connection form $remoteAddress to local ${listenAt.listenAddress}.")

    val networkDeviceInfo = new NetworkDeviceInfo(remoteAddress, listenAt.listenAddress, listenAt.restAddress)

    amaConfig.broadcaster ! new NewIncomingConnection(networkDeviceInfo, tcpActor)
  }

  protected def stopListeningAt(sla: StopListeningAt): Unit = {
    log.debug(s"Received $sla, stop listening at ${listenAt.listenAddress}.")
    context.stop(self)
  }
}
*/ 