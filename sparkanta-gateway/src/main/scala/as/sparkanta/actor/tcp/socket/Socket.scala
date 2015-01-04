package as.sparkanta.actor.tcp.socket

import scala.util.{ Try, Success, Failure }
import akka.actor.{ ActorRef, Actor, ActorLogging, Props, OneForOneStrategy, SupervisorStrategy }
import akka.util.ByteString
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message, OutgoingReplyOn2Message }
import scala.net.IdentifiedConnectionInfo
import as.sparkanta.gateway.NetworkAck
import as.sparkanta.ama.config.AmaConfig
import as.akka.broadcaster.Broadcaster
import as.ama.addon.lifecycle.ShutdownSystem
import scala.collection.mutable.Map

object Socket {

  class ListenAt(val connectionInfo: IdentifiedConnectionInfo, val akkaSocketTcpActor: ActorRef, val maximumQueuedSendDataMessages: Long) extends IncomingReplyableMessage
  abstract class ListenAtResult(val tryWasListening: Try[Boolean], listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(listenAt, listenAtSender)
  class ListenAtSuccessResult(val wasListening: Boolean, listenAt: ListenAt, listenAtSender: ActorRef) extends ListenAtResult(Success(wasListening), listenAt, listenAtSender)
  class ListenAtErrorResult(val exception: Exception, listenAt: ListenAt, listenAtSender: ActorRef) extends ListenAtResult(Failure(exception), listenAt, listenAtSender)
  class ListeningStarted(listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(listenAt, listenAtSender)

  class StopListeningAt(val id: Long) extends IncomingReplyableMessage
  abstract class StopListeningAtResult(val tryWasListening: Try[Boolean], stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn2Message(stopListeningAt, stopListeningAtSender, listenAt, listenAtSender)
  class StopListeningAtSuccessResult(val wasListening: Boolean, stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends StopListeningAtResult(Success(wasListening), stopListeningAt, stopListeningAtSender, listenAt, listenAtSender)
  class StopListeningAtErrorResult(val exception: Exception, stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends StopListeningAtResult(Failure(exception), stopListeningAt, stopListeningAtSender, listenAt, listenAtSender)
  class ListeningStopped(val listeningStopType: ListeningStopType, listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(listenAt, listenAtSender)

  class SendData(val data: ByteString, val id: Long, val ack: NetworkAck) extends IncomingReplyableMessage { def this(data: Array[Byte], id: Long, ack: NetworkAck) = this(ByteString(data), id, ack) }
  abstract class SendDataResult(val optionalException: Option[Exception], sendData: SendData, sendDataSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn2Message(sendData, sendDataSender, listenAt, listenAtSender)
  class SendDataSuccessResult(sendData: SendData, sendDataSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends SendDataResult(None, sendData, sendDataSender, listenAt, listenAtSender)
  class SendDataErrorResult(val exception: Exception, sendData: SendData, sendDataSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends SendDataResult(Some(exception), sendData, sendDataSender, listenAt, listenAtSender)

  class NewData(val data: ByteString, listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(listenAt, listenAtSender)

  sealed trait ListeningStopType extends Serializable
  object StoppedByRemoteSide extends ListeningStopType
  sealed trait StoppedByLocalSide extends ListeningStopType
  class StoppedBecauseOfLocalSideException(val exception: Exception) extends StoppedByLocalSide
  class StoppedBecauseOfLocalSideRequest(val stopListeningAt: StopListeningAt, val stopListeningAtSender: ActorRef) extends StoppedByLocalSide
}

class Socket(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import Socket._

  protected val map = Map[Long, ActorRef]() // remote address id -> socket worker

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new SocketClassifier(amaConfig.broadcaster))
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${getClass.getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: SendData         => sendData(a, sender)
    case a: ListenAt         => startListeningAt(a, sender)
    case a: StopListeningAt  => stopListeningAt(a, sender)

    case a: ListeningStarted => listeningStarted(a, sender)
    case a: ListeningStopped => listeningStopped(a)

    case message             => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def startListeningAt(listenAt: ListenAt, listenAtSender: ActorRef): Unit = map.get(listenAt.connectionInfo.remote.id) match {
    case Some(socketWorker) => {
      val listenAtSuccessResult = new ListenAtSuccessResult(true, listenAt, listenAtSender)
      listenAtSuccessResult.reply(self)
    }

    case None => {
      val props = Props(new SocketWorker(listenAt, listenAtSender, self))
      context.actorOf(props, name = classOf[SocketWorker].getSimpleName + "-" + listenAt.connectionInfo.remote.id)
    }
  }

  protected def sendData(sendData: SendData, sendDataSender: ActorRef): Unit = forwardOrExecute(sendData.id, sendData, sendDataSender) {
    val e = new Exception(s"There is no socket of remote address id ${sendData.id}.")
    val sendDataErrorResult = new SendDataErrorResult(e, sendData, sendDataSender, null, null)
    sendDataErrorResult.reply(self)
  }

  protected def listeningStarted(listeningStartedMessage: ListeningStarted, socketWorker: ActorRef): Unit =
    listeningStarted(listeningStartedMessage.request1.message.connectionInfo.remote.id, socketWorker)

  protected def listeningStarted(id: Long, socketWorker: ActorRef): Unit = map.get(id) match {

    case Some(socketWorker) => {
      val e = new Exception(s"Remote address id $id is already known (served by worker actor $socketWorker), could not add it again.")
      log.error(e, e.getMessage)
    }

    case None => {
      map.put(id, socketWorker)
      log.debug(s"Remote address id $id was added (worker actor $socketWorker), currently there are ${map.size} opened sockets (ids: ${map.keySet.mkString(",")}).")
    }
  }

  protected def listeningStopped(listeningStoppedMessage: ListeningStopped): Unit =
    listeningStopped(listeningStoppedMessage.request1.message.connectionInfo.remote.id)

  protected def listeningStopped(id: Long): Unit = map.remove(id).map { socketWorker =>
    log.debug(s"Remote address id $id was removed (worker actor $socketWorker), currently there are ${map.size} opened sockets (ids: ${map.keySet.mkString(",")}).")
  }

  protected def stopListeningAt(stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef): Unit = forwardOrExecute(stopListeningAt.id, stopListeningAt, stopListeningAtSender) {
    val successStopListeningAtResult = new StopListeningAtSuccessResult(false, stopListeningAt, stopListeningAtSender, null, null)
    successStopListeningAtResult.reply(self)
  }

  protected def forwardOrExecute(id: Long, message: Any, messageSender: ActorRef)(f: => Unit): Unit = map.get(id) match {
    case Some(socketWorker) => socketWorker.tell(message, messageSender)
    case None               => f
  }
}
