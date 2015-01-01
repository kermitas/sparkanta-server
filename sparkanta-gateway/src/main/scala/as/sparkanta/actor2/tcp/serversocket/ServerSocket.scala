package as.sparkanta.actor2.tcp.serversocket

import scala.util.{ Try, Success, Failure }
import akka.actor.{ ActorRef, Actor, ActorLogging, Props, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import scala.net.{ IdentifiedInetSocketAddress, IdentifiedConnectionInfo }
import java.util.concurrent.atomic.AtomicLong
import as.ama.addon.lifecycle.ShutdownSystem
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message, OutgoingReplyOn2Message }
import as.akka.broadcaster.MessageWithSender
import scala.collection.mutable.Map

object ServerSocket {

  class ListenAt(val listenAddress: IdentifiedInetSocketAddress, val openingServerSocketTimeoutInMs: Long, val keepOpenForMs: Long) extends IncomingReplyableMessage
  class StopListeningAt(val id: Long) extends IncomingReplyableMessage

  abstract class ListenAtResult(val wasOpened: Try[Boolean], listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(new MessageWithSender(listenAt, listenAtSender))
  class SuccessfulListenAtResult(wasOpened: Boolean, listenAt: ListenAt, listenAtSender: ActorRef) extends ListenAtResult(Success(wasOpened), listenAt, listenAtSender)
  class ErrorListenAtResult(exception: Exception, listenAt: ListenAt, listenAtSender: ActorRef) extends ListenAtResult(Failure(exception), listenAt, listenAtSender)
  class ListeningStarted(listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(new MessageWithSender(listenAt, listenAtSender))
  class ListeningStopped(val exception: Option[Exception], listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(new MessageWithSender(listenAt, listenAtSender))

  class StopListeningAtResult(val wasListening: Try[Boolean], stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn2Message(new MessageWithSender(stopListeningAt, stopListeningAtSender), new MessageWithSender(listenAt, listenAtSender))
  class SuccessStopListeningAtResult(wasListening: Boolean, stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends StopListeningAtResult(Success(wasListening), stopListeningAt, stopListeningAtSender, listenAt, listenAtSender)
  class ErrorStopListeningAtResult(exception: Exception, stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends StopListeningAtResult(Failure(exception), stopListeningAt, stopListeningAtSender, listenAt, listenAtSender)

  class NewConnection(val connectionInfo: IdentifiedConnectionInfo, val akkaSocketTcpActor: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(new MessageWithSender(listenAt, listenAtSender))
}

class ServerSocket(
  amaConfig:                        AmaConfig,
  remoteConnectionsUniqueNumerator: AtomicLong
) extends Actor with ActorLogging {

  def this(amaConfig: AmaConfig) = this(amaConfig, new AtomicLong(0))

  import ServerSocket._

  protected val map = Map[Long, ActorRef]()

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new ServerSocketClassifier(amaConfig.broadcaster))
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${classOf[ServerSocket].getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: ListenAt         => startListeningAt(a, sender)
    case a: StopListeningAt  => stopListeningAt(a, sender)
    case a: ListeningStarted => listeningStarted(a.request1.message.listenAddress.id, sender)
    case a: ListeningStopped => listeningStopped(a.request1.message.listenAddress.id)
    case message             => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def startListeningAt(listenAt: ListenAt, listenAtSender: ActorRef): Unit = forwardOrExecute(listenAt.listenAddress.id, listenAt, listenAtSender) {
    val props = Props(new ServerSocketWorker(listenAt, listenAtSender, self, remoteConnectionsUniqueNumerator, amaConfig.broadcaster))
    context.actorOf(props, name = classOf[ServerSocketWorker].getSimpleName + "-" + listenAt.listenAddress.id)
  }

  protected def listeningStarted(id: Long, serverSocketWorker: ActorRef): Unit = map.get(id) match {

    case Some(serverSocketWorker) => log.error(s"Listen address id $id is already known! Could not add it again!!")

    case None => {
      map.put(id, serverSocketWorker)
      log.debug(s"Listen address id $id was added (worker actor $serverSocketWorker), currently there are ${map.size} opened server sockets (ids: ${map.keySet.mkString(",")}).")
    }
  }

  protected def listeningStopped(id: Long): Unit = map.remove(id).map { serverSocketWorker =>
    log.debug(s"Listen address id $id was removed (worker actor $serverSocketWorker), currently there are ${map.size} opened server sockets (ids: ${map.keySet.mkString(",")}).")
  }

  protected def stopListeningAt(stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef): Unit = forwardOrExecute(stopListeningAt.id, stopListeningAt, stopListeningAtSender) {
    val successStopListeningAtResult = new SuccessStopListeningAtResult(false, stopListeningAt, stopListeningAtSender, null, null) // TODO do something with those nulls
    successStopListeningAtResult.reply(self)
  }

  protected def forwardOrExecute(id: Long, message: Any, messageSender: ActorRef)(f: => Unit): Unit = map.get(id) match {
    case Some(serverSocketWorker) => serverSocketWorker.tell(message, messageSender)
    case None                     => f
  }
}
