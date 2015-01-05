package as.sparkanta.actor.tcp.socket

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ FSM, ActorRef, Cancellable }
import akka.util.ByteString
import akka.io.Tcp
import akka.util.{ FSMSuccessOrStop, InternalMessage }
import scala.collection.mutable.ListBuffer
import as.sparkanta.gateway.{ NoAck, TcpAck }
import akka.util.MessageWithSender

object SocketWorker {
  sealed trait State extends Serializable
  case object WaitingForDataToSend extends State
  case object WaitingForTcpAck extends State

  sealed trait StateData extends Serializable
  case object WaitingForDataToSendStateData extends StateData
  case class WaitingForTcpAckStateData(current: MessageWithSender[Socket.SendData], ackTimeout: Cancellable) extends StateData

  object AckTimeout extends InternalMessage
  object TcpAck extends Tcp.Event
}

class SocketWorker(listenAt: Socket.ListenAt, listenAtSender: ActorRef, socketActor: ActorRef) extends FSM[SocketWorker.State, SocketWorker.StateData] with FSMSuccessOrStop[SocketWorker.State, SocketWorker.StateData] {

  import SocketWorker._
  import context.dispatcher

  protected val buffer = new ListBuffer[MessageWithSender[Socket.SendData]]

  startWith(WaitingForDataToSend, WaitingForDataToSendStateData)

  when(WaitingForDataToSend) {
    case Event(a: Socket.SendData, WaitingForDataToSendStateData) => successOrStopWithFailure { sendData(a, sender) }
  }

  when(WaitingForTcpAck) {
    case Event(a: Socket.SendData, sd: WaitingForTcpAckStateData) => successOrStopWithFailure { bufferSendData(a, sender, sd) }
    case Event(TcpAck, sd: WaitingForTcpAckStateData)             => successOrStopWithFailure { receivedTcpAck(sd) }
    case Event(AckTimeout, sd: WaitingForTcpAckStateData)         => successOrStopWithFailure { ackTimeout(sd) }
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(Tcp.Received(data), stateData)               => successOrStopWithFailure { receivedData(data, sender) }
    case Event(Tcp.CommandFailed(failedCommand), stateData) => successOrStopWithFailure { stop(FSM.Failure(new Exception(s"Command ${failedCommand.getClass.getSimpleName} ($failedCommand) failed."))) }
    case Event(a: Socket.StopListeningAt, stateData)        => successOrStopWithFailure { stopListeningAt(a, sender) }
    case Event(Tcp.PeerClosed, stateData)                   => successOrStopWithFailure { stop(FSM.Failure(Socket.StoppedByRemoteSide)) }

    case Event(unknownMessage, stateData) => {
      log.warning(s"Received unknown message '$unknownMessage' in state $stateName (state data $stateData)")
      stay using stateData
    }
  }

  onTermination {
    case StopEvent(reason, currentState, stateData) => terminate(reason, currentState, stateData)
  }

  initialize

  override def preStart(): Unit = {
    listenAt.akkaSocketTcpActor ! new Tcp.Register(self)

    val listenAtSuccessResult = new Socket.ListenAtSuccessResult(false, listenAt, listenAtSender)
    val listeningStarted = new Socket.ListeningStarted(listenAt, listenAtSender)

    socketActor ! listeningStarted
    listenAtSuccessResult.reply(socketActor)
    listeningStarted.reply(socketActor)
  }

  protected def sendData(sendData: Socket.SendData, sendDataSender: ActorRef) = if (sendData.id == listenAt.connectionInfo.remote.id) {

    sendData.ack match {
      case NoAck => {
        listenAt.akkaSocketTcpActor ! new Tcp.Write(sendData.data, Tcp.NoAck)
        val sendDataSuccessResult = new Socket.SendDataSuccessResult(sendData, sendDataSender, listenAt, listenAtSender)
        sendDataSuccessResult.reply(socketActor)
        goto(WaitingForDataToSend) using WaitingForDataToSendStateData
      }

      case a: TcpAck => {
        listenAt.akkaSocketTcpActor ! new Tcp.Write(sendData.data, TcpAck)
        val ackTimeout = context.system.scheduler.scheduleOnce(a.timeoutInMillis millis, self, AckTimeout)
        goto(WaitingForTcpAck) using new WaitingForTcpAckStateData(new MessageWithSender(sendData, sendDataSender), ackTimeout)
      }
    }
  } else {
    val exception = new Exception(s"Send data id ${sendData.id} does not match ${listenAt.connectionInfo.remote.id}.")
    val sendDataErrorResult = new Socket.SendDataErrorResult(exception, sendData, sendDataSender, listenAt, listenAtSender)
    sendDataErrorResult.reply(socketActor)
    goto(WaitingForDataToSend) using WaitingForDataToSendStateData
  }

  protected def bufferSendData(sendData: Socket.SendData, sendDataSender: ActorRef, sd: WaitingForTcpAckStateData) = {
    if (buffer.size >= listenAt.maximumQueuedSendDataMessages) {
      val exception = new Exception(s"Maximum (${listenAt.maximumQueuedSendDataMessages}) queued ${sendData.getClass.getSimpleName} messages reached.")
      val sendDataErrorResult = new Socket.SendDataErrorResult(exception, sendData, sendDataSender, listenAt, listenAtSender)
      sendDataErrorResult.reply(socketActor)
    } else {
      buffer += new MessageWithSender(sendData, sendDataSender)
    }

    stay using sd
  }

  protected def receivedTcpAck(sd: WaitingForTcpAckStateData) = {
    sd.ackTimeout.cancel

    val sendDataSuccessResult = new Socket.SendDataSuccessResult(sd.current.message, sd.current.messageSender, listenAt, listenAtSender)
    sendDataSuccessResult.reply(socketActor)

    pickupNextTaskOrGotoWaitingForDataToSend
  }

  protected def pickupNextTaskOrGotoWaitingForDataToSend = if (buffer.nonEmpty) {

    def sendNextDataToSend = {
      val messageWithSender = buffer.head
      buffer -= messageWithSender
      sendData(messageWithSender.message, messageWithSender.messageSender)
    }

    var nextState = sendNextDataToSend

    while (buffer.nonEmpty && nextState.stateName == WaitingForDataToSend) nextState = sendNextDataToSend

    nextState

  } else {
    goto(WaitingForDataToSend) using WaitingForDataToSendStateData
  }

  protected def ackTimeout(sd: WaitingForTcpAckStateData) = stop(FSM.Failure(new Exception(s"Timeout (${sd.current.message.ack.asInstanceOf[TcpAck].timeoutInMillis} milliseconds) while waiting for tcp ack.")))

  protected def receivedData(data: ByteString, dataSender: ActorRef) = {
    log.debug(s"Received ${data.size} bytes from ${listenAt.connectionInfo}.")
    val newData = new Socket.NewData(data, listenAt, listenAtSender)
    newData.reply(socketActor)
    stay using stateData
  }

  protected def stopListeningAt(stopListeningAt: Socket.StopListeningAt, stopListeningAtSender: ActorRef) = if (stopListeningAt.id == listenAt.connectionInfo.remote.id) {
    stop(FSM.Failure(new Socket.StoppedBecauseOfLocalSideRequest(stopListeningAt, sender)))
  } else {
    val exception = new Exception(s"Received stop listening id ${stopListeningAt.id} does not match ${listenAt.connectionInfo.remote.id}.")
    val stopListeningAtErrorResult = new Socket.StopListeningAtErrorResult(exception, stopListeningAt, sender, listenAt, listenAtSender)
    stopListeningAtErrorResult.reply(socketActor)
    stay using stateData
  }

  protected def terminate(reason: FSM.Reason, currentState: SocketWorker.State, stateData: SocketWorker.StateData): Unit = {

    val listeningStopType: Socket.ListeningStopType = reason match {
      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData.")
        new Socket.StoppedBecauseOfLocalSideException(new Exception(s"${getClass.getSimpleName} actor was stopped normally."))
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
        new Socket.StoppedBecauseOfLocalSideException(new Exception(s"${getClass.getSimpleName} actor was shutdown."))
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")

        cause match {
          case a: Socket.ListeningStopType => a
          case e: Exception                => new Socket.StoppedBecauseOfLocalSideException(e)
          case u                           => new Socket.StoppedBecauseOfLocalSideException(new Exception(s"Unknown stop cause of type ${u.getClass.getSimpleName}, $u."))
        }
      }
    }

    val exception = listeningStopType match {
      case a: Socket.StoppedBecauseOfLocalSideException => a.exception
      case a: Socket.StoppedBecauseOfLocalSideRequest   => new Exception(s"Connection closed by local side ${a.stopListeningAt.getClass.getSimpleName} request.")
      case Socket.StoppedByRemoteSide                   => new Exception("Remote side closed connection.")
    }

    listenAt.akkaSocketTcpActor ! Tcp.Close

    buffer.foreach { messageWithSender =>
      val sendDataErrorResult = new Socket.SendDataErrorResult(exception, messageWithSender.message, messageWithSender.messageSender, listenAt, listenAtSender)
      sendDataErrorResult.reply(socketActor)
    }

    if (stateData.isInstanceOf[WaitingForTcpAckStateData]) {
      val waitingForTcpAckStateData = stateData.asInstanceOf[WaitingForTcpAckStateData]

      waitingForTcpAckStateData.ackTimeout.cancel

      val sendDataErrorResult = new Socket.SendDataErrorResult(exception, waitingForTcpAckStateData.current.message, waitingForTcpAckStateData.current.messageSender, listenAt, listenAtSender)
      sendDataErrorResult.reply(socketActor)
    }

    val listeningStopped = new Socket.ListeningStopped(listeningStopType, listenAt, listenAtSender)
    socketActor ! listeningStopped
    listeningStopped.reply(socketActor)
  }

}
