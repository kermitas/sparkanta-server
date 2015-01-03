package as.sparkanta.actor.device

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, FSM, Cancellable }
import akka.util.{ FSMSuccessOrStop, InternalMessage }
import as.sparkanta.gateway.{ Device => DeviceSpec, NoAck, TcpAck, DeviceAck }
import as.sparkanta.actor.tcp.socket.Socket
import scala.collection.mutable.ListBuffer
import as.sparkanta.actor.message.serializer.Serializer
import as.sparkanta.device.message.todevice.{ NoAck => DeviceNoAck }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.device.message.fromdevice.Ack

object DeviceMessageSenderWorker {
  sealed trait State extends Serializable
  case object WaitingForDataToSend extends State
  case object WaitingForTcpAck extends State
  case object WaitingForDeviceAck extends State

  sealed trait StateData extends Serializable
  case object WaitingForDataToSendStateData extends StateData
  case class WaitingForTcpAckStateData(current: Record, timeout: Cancellable, timeoutInMs: Long) extends StateData
  case class WaitingForDeviceAckStateData(current: Record, timeout: Cancellable, timeoutInMs: Long) extends StateData

  object TcpAckTimeout extends InternalMessage
  object DeviceAckTimeout extends InternalMessage

  class Record(val sendMessage: DeviceSpec.SendMessage, val sendMessageSender: ActorRef, val serializedMessage: Array[Byte])

  class SerializeWithSendMessage123(val sendMessage: DeviceSpec.SendMessage, val sendMessageSender: ActorRef) extends Serializer.Serialize(sendMessage.messageToDevice, if (sendMessage.ack.isInstanceOf[DeviceAck]) sendMessage.ack.asInstanceOf[DeviceAck].deviceAck else DeviceNoAck)
}

class DeviceMessageSenderWorker(
  id:                            Long,
  broadcaster:                   ActorRef,
  deviceMessageSenderActor:      ActorRef,
  maximumQueuedSendDataMessages: Long
) extends FSM[DeviceMessageSenderWorker.State, DeviceMessageSenderWorker.StateData] with FSMSuccessOrStop[DeviceMessageSenderWorker.State, DeviceMessageSenderWorker.StateData] {

  import DeviceMessageSenderWorker._
  import context.dispatcher

  protected val buffer = new ListBuffer[Record]

  startWith(WaitingForDataToSend, WaitingForDataToSendStateData)

  when(WaitingForDataToSend) {
    case Event(a: DeviceSpec.SendMessage, stateData)                                    => successOrStopWithFailure { serializeMessage(a, sender) }
    case Event(a: Serializer.SerializationSuccessResult, sd: WaitingForTcpAckStateData) => successOrStopWithFailure { sendData(a) }
    case Event(a: Socket.SendDataSuccessResult, stateData)                              => stay using stateData
  }

  when(WaitingForTcpAck) {
    case Event(a: DeviceSpec.SendMessage, stateData)                           => successOrStopWithFailure { serializeMessage(a, sender) }
    case Event(a: Serializer.SerializationSuccessResult, stateData)            => successOrStopWithFailure { bufferDataToSend(a) }

    case Event(a: Socket.SendDataSuccessResult, sd: WaitingForTcpAckStateData) => successOrStopWithFailure { stay using sd } // TODO !!!!!
    case Event(TcpAckTimeout, sd: WaitingForTcpAckStateData)                   => successOrStopWithFailure { tcpAckTimeout(sd) }
  }

  when(WaitingForDeviceAck) {
    case Event(a: DeviceSpec.SendMessage, stateData)                => successOrStopWithFailure { serializeMessage(a, sender) }
    case Event(a: Serializer.SerializationSuccessResult, stateData) => successOrStopWithFailure { bufferDataToSend(a) }
    case Event(a: Socket.SendDataSuccessResult, stateData)          => stay using stateData

    case Event(a: Ack, sd: WaitingForDeviceAckStateData)            => successOrStopWithFailure { stay using sd } // TODO !!!!!
    case Event(DeviceAckTimeout, sd: WaitingForDeviceAckStateData)  => successOrStopWithFailure { deviceAckTimeout(sd) }
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(a: Serializer.SerializationErrorResult, stateData) => successOrStopWithFailure { serializationError(a) }
    case Event(a: Socket.SendDataErrorResult, stateData)          => successOrStopWithFailure { sendDataError(a.exception.get) }
    case Event(_: Socket.ListeningStopped, stateData)             => stop(FSM.Normal)

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
    broadcaster ! new Broadcaster.Register(self, new DeviceMessageSenderWorkerClassifier(id, broadcaster))
  }

  protected def serializeMessage(sendMessage: DeviceSpec.SendMessage, sendMessageSender: ActorRef) = {
    broadcaster ! new SerializeWithSendMessage123(sendMessage, sendMessageSender)
    stay using stateData
  }

  protected def serializationError(a: Serializer.SerializationErrorResult): State =
    serializationError(a.exception, a.request1.message.asInstanceOf[SerializeWithSendMessage123])

  protected def serializationError(exception: Exception, serializeWithSendMessage: SerializeWithSendMessage123): State =
    serializationError(exception, serializeWithSendMessage.sendMessage, serializeWithSendMessage.sendMessageSender)

  protected def serializationError(exception: Exception, sendMessage: DeviceSpec.SendMessage, sendMessageSender: ActorRef): State = {
    val sendMessageErrorResult = new DeviceSpec.SendMessageErrorResult(exception, sendMessage, sendMessageSender)
    sendMessageErrorResult.reply(deviceMessageSenderActor)
    stop(FSM.Failure(new Exception("Stopping because of problem with message serialization.", exception)))
  }

  protected def sendDataError(exception: Exception) = stop(FSM.Failure(new Exception("Stopping because of problem with sending data.", exception)))

  protected def sendData(a: Serializer.SerializationSuccessResult): State =
    sendData(a.serializedMessageToDevice.get, a.request1.message.asInstanceOf[SerializeWithSendMessage123])

  protected def sendData(serializedMessage: Array[Byte], serializeWithSendMessage: SerializeWithSendMessage123): State =
    sendData(serializedMessage, serializeWithSendMessage.sendMessage, serializeWithSendMessage.sendMessageSender)

  protected def sendData(serializedMessage: Array[Byte], sendMessage: DeviceSpec.SendMessage, sendMessageSender: ActorRef): State = sendMessage.ack match {
    case NoAck => {
      broadcaster ! new Socket.SendData(serializedMessage, id, NoAck)

      val sendMessageSuccessResult = new DeviceSpec.SendMessageSuccessResult(sendMessage, sendMessageSender)
      sendMessageSuccessResult.reply(deviceMessageSenderActor)

      goto(WaitingForDataToSend) using WaitingForDataToSendStateData
    }

    case tcpAck: TcpAck => {
      broadcaster ! new Socket.SendData(serializedMessage, id, tcpAck)

      val record = new Record(sendMessage, sendMessageSender, serializedMessage)
      val timeout = context.system.scheduler.scheduleOnce(tcpAck.timeoutInMillis millis, self, TcpAckTimeout)

      goto(WaitingForTcpAck) using new WaitingForTcpAckStateData(record, timeout, tcpAck.timeoutInMillis)
    }

    case deviceAck: DeviceAck => {
      broadcaster ! new Socket.SendData(serializedMessage, id, NoAck)

      val record = new Record(sendMessage, sendMessageSender, serializedMessage)
      val timeout = context.system.scheduler.scheduleOnce(deviceAck.timeoutInMillis millis, self, DeviceAckTimeout)

      goto(WaitingForDeviceAck) using new WaitingForDeviceAckStateData(record, timeout, deviceAck.timeoutInMillis)
    }
  }

  protected def bufferDataToSend(a: Serializer.SerializationSuccessResult): State =
    bufferDataToSend(a.serializedMessageToDevice.get, a.request1.message.asInstanceOf[SerializeWithSendMessage123])

  protected def bufferDataToSend(serializedMessage: Array[Byte], serializeWithSendMessage: SerializeWithSendMessage123): State =
    bufferDataToSend(serializedMessage, serializeWithSendMessage.sendMessage, serializeWithSendMessage.sendMessageSender)

  protected def bufferDataToSend(serializedMessage: Array[Byte], sendMessage: DeviceSpec.SendMessage, sendMessageSender: ActorRef): State =
    bufferDataToSend(new Record(sendMessage, sendMessageSender, serializedMessage))

  protected def bufferDataToSend(record: Record): State = {
    buffer += record

    if (buffer.size >= maximumQueuedSendDataMessages) {
      throw new Exception(s"Maximum number $maximumQueuedSendDataMessages of buffered messages to send reached.")
    } else {
      stay using stateData
    }
  }

  protected def tcpAckTimeout(sd: WaitingForTcpAckStateData) = {
    val e = new Exception(s"Timeout (${sd.timeoutInMs} milliseconds) while waiting for tcp ack.")
    sendMessageErrorResult(e, sd.current)
    stop(FSM.Failure(e))
  }

  protected def deviceAckTimeout(sd: WaitingForDeviceAckStateData) = {
    val e = new Exception(s"Timeout (${sd.timeoutInMs} milliseconds) while waiting for device ack.")
    sendMessageErrorResult(e, sd.current)
    stop(FSM.Failure(e))
  }

  protected def terminate(reason: FSM.Reason, currentState: DeviceMessageSenderWorker.State, stateData: DeviceMessageSenderWorker.StateData): Unit = {

    val exception = reason match {
      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData.")
        new Exception(s"${getClass.getSimpleName} actor was stopped normally.")
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
        new Exception(s"${getClass.getSimpleName} actor was shutdown.")
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")

        cause match {
          case e: Exception => e
          case u            => new Exception(s"Unknown stop cause of type ${u.getClass.getSimpleName}, $u.")
        }
      }
    }

    broadcaster ! new Socket.StopListeningAt(id)

    /*
    listenAt.akkaSocketTcpActor ! Tcp.Close

    if (stateData.isInstanceOf[WaitingForTcpAckStateData]) {
      val waitingForTcpAckStateData = stateData.asInstanceOf[WaitingForTcpAckStateData]

      waitingForTcpAckStateData.ackTimeout.cancel

      val sendDataErrorResult = new Socket.SendDataErrorResult(exception, waitingForTcpAckStateData.current.message, waitingForTcpAckStateData.current.messageSender, listenAt, listenAtSender)
      sendDataErrorResult.reply(socketActor)
    }

    buffer.foreach { messageWithSender =>
      val sendDataErrorResult = new Socket.SendDataErrorResult(exception, messageWithSender.message, messageWithSender.messageSender, listenAt, listenAtSender)
      sendDataErrorResult.reply(socketActor)
    }

    val listeningStopped = new Socket.ListeningStopped(listeningStopType, listenAt, listenAtSender)
    socketActor ! listeningStopped
    listeningStopped.reply(socketActor)*/

    sendMessageErrorResult(exception, buffer)
  }

  protected def sendMessageErrorResult(exception: Exception, record: Seq[Record]): Unit =
    record.foreach(sendMessageErrorResult(exception, _))

  protected def sendMessageErrorResult(exception: Exception, record: Record): Unit =
    sendMessageErrorResult(exception, record.sendMessage, record.sendMessageSender)

  protected def sendMessageErrorResult(exception: Exception, sendMessage: DeviceSpec.SendMessage, sendMessageSender: ActorRef): Unit = {
    val sendMessageErrorResult = new DeviceSpec.SendMessageErrorResult(exception, sendMessage, sendMessageSender)
    sendMessageErrorResult.reply(deviceMessageSenderActor)
  }

  /*
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
    val e = new Exception(s"Send data id ${sendData.id} does not match ${listenAt.connectionInfo.remote.id}.")
    val sendDataErrorResult = new Socket.SendDataErrorResult(e, sendData, sendDataSender, listenAt, listenAtSender)
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
    stop(FSM.Failure(new Socket.StoppedByLocalSideRequest(stopListeningAt, sender)))
  } else {
    val e = new Exception(s"Received stop listening id ${stopListeningAt.id} does not match ${listenAt.connectionInfo.remote.id}.")
    val stopListeningAtErrorResult = new Socket.StopListeningAtErrorResult(e, stopListeningAt, sender, listenAt, listenAtSender)
    stopListeningAtErrorResult.reply(socketActor)
    stay using stateData
  }

  protected def terminate(reason: FSM.Reason, currentState: SocketWorker.State, stateData: SocketWorker.StateData): Unit = {

    val listeningStopType: Socket.ListeningStopType = reason match {
      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData.")
        new Socket.StoppedByLocalSideException(new Exception(s"${getClass.getSimpleName} actor was stopped normally."))
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
        new Socket.StoppedByLocalSideException(new Exception(s"${getClass.getSimpleName} actor was shutdown."))
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")

        cause match {
          case a: Socket.ListeningStopType => a
          case e: Exception                => new Socket.StoppedByLocalSideException(e)
          case u                           => new Socket.StoppedByLocalSideException(new Exception(s"Unknown stop cause of type ${u.getClass.getSimpleName}, $u."))
        }
      }
    }

    val exception = listeningStopType match {
      case a: Socket.StoppedByLocalSideException => a.exception
      case a: Socket.StoppedByLocalSideRequest   => new Exception(s"Connection closed by local side ${a.stopListeningAt.getClass.getSimpleName} request.")
      case Socket.StoppedByRemoteSide            => new Exception("Remote side closed connection.")
    }

    listenAt.akkaSocketTcpActor ! Tcp.Close

    if (stateData.isInstanceOf[WaitingForTcpAckStateData]) {
      val waitingForTcpAckStateData = stateData.asInstanceOf[WaitingForTcpAckStateData]

      waitingForTcpAckStateData.ackTimeout.cancel

      val sendDataErrorResult = new Socket.SendDataErrorResult(exception, waitingForTcpAckStateData.current.message, waitingForTcpAckStateData.current.messageSender, listenAt, listenAtSender)
      sendDataErrorResult.reply(socketActor)
    }

    buffer.foreach { messageWithSender =>
      val sendDataErrorResult = new Socket.SendDataErrorResult(exception, messageWithSender.message, messageWithSender.messageSender, listenAt, listenAtSender)
      sendDataErrorResult.reply(socketActor)
    }

    val listeningStopped = new Socket.ListeningStopped(listeningStopType, listenAt, listenAtSender)
    socketActor ! listeningStopped
    listeningStopped.reply(socketActor)
  }
  */
}
