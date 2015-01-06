package as.sparkanta.actor.device1.message.serializer

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, Cancellable, FSM, ActorRefFactory, Props }
import akka.util.{ FSMSuccessOrStop, InternalMessage }
import as.sparkanta.gateway.{ Device, DeviceAck, NoAck, TcpAck }
import as.sparkanta.device.message.todevice.{ NoAck => DeviceNoAck }
import as.sparkanta.actor.message.serializer.{ Serializer => GeneralSerializer }
import scala.collection.mutable.ListBuffer
import as.akka.broadcaster.Broadcaster
import as.sparkanta.device.message.fromdevice.Ack
import as.sparkanta.actor.tcp.socket.Socket

object SerializerWorker {

  lazy final val waitingForSendDataResultTimeoutInMsIfNotSet = 5 * 1000

  sealed trait State extends Serializable
  case object WaitingForDataToSend extends State
  case object WaitingForSendDataResult extends State
  case object WaitingForDeviceAck extends State

  sealed trait StateData extends Serializable
  case object WaitingForDataToSendStateData extends StateData
  case class WaitingForSendDataResultStateData(current: Record, timeout: Cancellable, timeoutInMs: Long) extends StateData
  case class WaitingForDeviceAckStateData(current: Record, timeout: Cancellable, timeoutInMs: Long) extends StateData

  object Timeout extends InternalMessage

  class Record(val sendMessage: Device.SendMessage, val sendMessageSender: ActorRef, val serializedMessage: Array[Byte])

  class SerializeWithSendMessage(val sendMessage: Device.SendMessage, val sendMessageSender: ActorRef)
    extends GeneralSerializer.Serialize(sendMessage.messageToDevice, if (sendMessage.ack.isInstanceOf[DeviceAck]) sendMessage.ack.asInstanceOf[DeviceAck].deviceAck else DeviceNoAck)

  def startActor(actorRefFactory: ActorRefFactory, id: Long, broadcaster: ActorRef, deviceActor: ActorRef, maximumQueuedSendDataMessages: Long): ActorRef = {
    val props = Props(new SerializerWorker(id, broadcaster, deviceActor, maximumQueuedSendDataMessages))
    val actor = actorRefFactory.actorOf(props, name = classOf[SerializerWorker].getSimpleName + "-" + id)
    broadcaster ! new Broadcaster.Register(actor, new SerializerWorkerClassifier(id, broadcaster))
    actor
  }
}

class SerializerWorker(id: Long, broadcaster: ActorRef, var deviceActor: ActorRef, maximumQueuedSendDataMessages: Long)
  extends FSM[SerializerWorker.State, SerializerWorker.StateData] with FSMSuccessOrStop[SerializerWorker.State, SerializerWorker.StateData] {

  import SerializerWorker._
  import context.dispatcher

  protected val buffer = new ListBuffer[Record]

  startWith(WaitingForDataToSend, WaitingForDataToSendStateData)

  when(WaitingForDataToSend) {
    case Event(a: GeneralSerializer.SerializationSuccessResult, _) => successOrStopWithFailure { serializationSuccess(a) }
    case Event(_: Socket.SendDataSuccessResult, sd)                => stay using sd
    case Event(a: Socket.SendDataErrorResult, _)                   => successOrStopWithFailure { sendDataError(a) }
  }

  when(WaitingForSendDataResult) {
    case Event(_: Socket.SendDataSuccessResult, sd: WaitingForSendDataResultStateData) => successOrStopWithFailure { sendDataSuccess(sd) }
    case Event(a: GeneralSerializer.SerializationSuccessResult, _)                     => successOrStopWithFailure { bufferSerializationSuccess(a) }
    case Event(a: Socket.SendDataErrorResult, _)                                       => successOrStopWithFailure { sendDataError(a) }
    case Event(Timeout, sd: WaitingForSendDataResultStateData)                         => successOrStopWithFailure { timeout(sd) }
  }

  when(WaitingForDeviceAck) {
    case Event(a: Ack, sd: WaitingForDeviceAckStateData)           => successOrStopWithFailure { deviceAck(a, sd) }
    case Event(_: Socket.SendDataSuccessResult, sd)                => stay using stateData
    case Event(a: GeneralSerializer.SerializationSuccessResult, _) => successOrStopWithFailure { bufferSerializationSuccess(a) }
    case Event(a: Socket.SendDataErrorResult, _)                   => successOrStopWithFailure { sendDataError(a) }
    case Event(Timeout, sd: WaitingForDeviceAckStateData)          => successOrStopWithFailure { timeout(sd) }
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(a: Device.SendMessage, _)                         => successOrStopWithFailure { sendMessage(a, sender) }
    case Event(a: GeneralSerializer.SerializationErrorResult, _) => successOrStopWithFailure { serializationError(a) }
    case Event(a: Device.StartErrorResult, _)                    => successOrStopWithFailure { deviceStartError }
    case Event(a: Device.StartSuccessResult, _)                  => successOrStopWithFailure { deviceStartSuccess(sender) }
    case Event(a: Device.Stopped, _)                             => successOrStopWithFailure { deviceStopped }
    case Event(_: Ack, _)                                        => stay using stateData

    case Event(unknownMessage, stateData) => {
      log.warning(s"Received unknown message '$unknownMessage' in state $stateName (state data $stateData)")
      stay using stateData
    }
  }

  onTermination {
    case StopEvent(reason, currentState, stateData) => terminate(reason, currentState, stateData)
  }

  initialize

  protected def sendMessage(sendMessage: Device.SendMessage, sendMessageSender: ActorRef) = {
    broadcaster ! new SerializeWithSendMessage(sendMessage, sendMessageSender)
    stay using stateData
  }

  protected def serializationError(serializationErrorResult: GeneralSerializer.SerializationErrorResult): State =
    serializationError(serializationErrorResult.exception, serializationErrorResult.request1.message.asInstanceOf[SerializeWithSendMessage])

  protected def serializationError(exception: Exception, serializeWithSendMessage: SerializeWithSendMessage): State =
    serializationError(exception, serializeWithSendMessage.sendMessage, serializeWithSendMessage.sendMessageSender)

  protected def serializationError(exception: Exception, sendMessage: Device.SendMessage, sendMessageSender: ActorRef): State = {
    buffer += new Record(sendMessage, sendMessageSender, Array.empty)
    val e = new Exception("Message to device serialization problem.", exception)
    stop(FSM.Failure(e))
  }

  protected def deviceStartError = stop(FSM.Normal)

  protected def deviceStartSuccess(startSuccessResultSender: ActorRef) = {
    deviceActor = startSuccessResultSender
    stay using stateData
  }

  protected def deviceStopped = stop(FSM.Normal)

  protected def serializationSuccess(serializationSuccessResult: GeneralSerializer.SerializationSuccessResult): State =
    serializationSuccess(serializationSuccessResult.serializedMessageToDevice, serializationSuccessResult.request1.message.asInstanceOf[SerializeWithSendMessage])

  protected def serializationSuccess(serializedMessageToDevice: Array[Byte], serializeWithSendMessage: SerializeWithSendMessage): State =
    serializationSuccess(serializedMessageToDevice, serializeWithSendMessage.sendMessage, serializeWithSendMessage.sendMessageSender)

  protected def serializationSuccess(serializedMessageToDevice: Array[Byte], sendMessageMessage: Device.SendMessage, sendMessageSender: ActorRef): State =
    sendMessage(serializedMessageToDevice, sendMessageMessage, sendMessageSender)

  protected def sendMessage(serializedMessageToDevice: Array[Byte], sendMessageMessage: Device.SendMessage, sendMessageSender: ActorRef): State = sendMessageMessage.ack match {
    case NoAck => {
      broadcaster ! new Socket.SendData(serializedMessageToDevice, id, NoAck)
      val sendMessageSuccessResult = new Device.SendMessageSuccessResult(sendMessageMessage, sendMessageSender)
      sendMessageSuccessResult.reply(deviceActor)
      goto(WaitingForDataToSend) using WaitingForDataToSendStateData
    }

    case tcpAck: TcpAck => {
      broadcaster ! new Socket.SendData(serializedMessageToDevice, id, tcpAck)
      val timeout = context.system.scheduler.scheduleOnce(tcpAck.timeoutInMillis millis, self, Timeout)
      val record = new Record(sendMessageMessage, sendMessageSender, serializedMessageToDevice)
      goto(WaitingForSendDataResult) using new WaitingForSendDataResultStateData(record, timeout, tcpAck.timeoutInMillis)
    }

    case deviceAck: DeviceAck => {
      broadcaster ! new Socket.SendData(serializedMessageToDevice, id, NoAck)
      val timeout = context.system.scheduler.scheduleOnce(deviceAck.timeoutInMillis millis, self, Timeout)
      val record = new Record(sendMessageMessage, sendMessageSender, serializedMessageToDevice)
      goto(WaitingForDeviceAck) using new WaitingForDeviceAckStateData(record, timeout, deviceAck.timeoutInMillis)
    }
  }

  protected def timeout(sd: WaitingForSendDataResultStateData) = stop(FSM.Failure(new Exception(s"Timeout (${sd.timeoutInMs} milliseconds) while waiting for tcp ack reached.")))

  protected def timeout(sd: WaitingForDeviceAckStateData) = stop(FSM.Failure(new Exception(s"Timeout (${sd.timeoutInMs} milliseconds) while waiting for device ack reached.")))

  protected def bufferSerializationSuccess(serializationSuccessResult: GeneralSerializer.SerializationSuccessResult): State =
    bufferSerializationSuccess(serializationSuccessResult.serializedMessageToDevice, serializationSuccessResult.request1.message.asInstanceOf[SerializeWithSendMessage])

  protected def bufferSerializationSuccess(serializedMessageToDevice: Array[Byte], serializeWithSendMessage: SerializeWithSendMessage): State =
    bufferSerializationSuccess(serializedMessageToDevice, serializeWithSendMessage.sendMessage, serializeWithSendMessage.sendMessageSender)

  protected def bufferSerializationSuccess(serializedMessageToDevice: Array[Byte], sendMessageMessage: Device.SendMessage, sendMessageSender: ActorRef): State = {
    buffer += new Record(sendMessageMessage, sendMessageSender, serializedMessageToDevice)

    if (buffer.size >= maximumQueuedSendDataMessages) {
      stop(FSM.Failure(s"Maximum ($maximumQueuedSendDataMessages) buffered messages count reached."))
    } else {
      stay using stateData
    }
  }

  protected def sendDataError(sendDataErrorResult: Socket.SendDataErrorResult) =
    stop(FSM.Failure(new Exception("Problem with sending data to device.", sendDataErrorResult.exception)))

  protected def sendDataSuccess(sd: WaitingForSendDataResultStateData) = {
    sd.timeout.cancel

    val sendMessageSuccessResult = new Device.SendMessageSuccessResult(sd.current.sendMessage, sd.current.sendMessageSender)
    sendMessageSuccessResult.reply(deviceActor)

    pickUpNextTaskOrGoToInitialState
  }

  protected def deviceAck(ack: Ack, sd: WaitingForDeviceAckStateData) = if (ack.ackedMessageCode == sd.current.sendMessage.messageToDevice.messageCode) {
    sd.timeout.cancel

    val sendMessageSuccessResult = new Device.SendMessageSuccessResult(sd.current.sendMessage, sd.current.sendMessageSender)
    sendMessageSuccessResult.reply(deviceActor)

    pickUpNextTaskOrGoToInitialState
  } else {
    stay using sd
  }

  protected def pickUpNextTaskOrGoToInitialState = if (buffer.nonEmpty) {

    def sendNextDataToSend = {
      val record = buffer.head
      buffer -= record
      sendMessage(record.serializedMessage, record.sendMessage, record.sendMessageSender)
    }

    var nextState = sendNextDataToSend

    while (buffer.nonEmpty && nextState.stateName == WaitingForDataToSend) nextState = sendNextDataToSend

    nextState

  } else {
    goto(WaitingForDataToSend) using WaitingForDataToSendStateData
  }

  protected def terminate(reason: FSM.Reason, currentState: SerializerWorker.State, stateData: SerializerWorker.StateData): Unit = {

    val exception = reason match {
      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData.")
        None
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
        Some(new Exception(s"${getClass.getSimpleName} actor was shutdown."))
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")

        cause match {
          case e: Exception => Some(e)
          case u            => Some(new Exception(s"Unknown stop cause of type ${u.getClass.getSimpleName}, $u."))
        }
      }
    }

    {
      val e = exception.getOrElse(new Exception(s"Actor ${getClass.getSimpleName} was stopped."))

      sendMessageErrorResult(e, buffer)

      stateData match {
        case sd: WaitingForSendDataResultStateData => {
          sd.timeout.cancel
          sendMessageErrorResult(e, sd.current)
        }

        case sd: WaitingForDeviceAckStateData => {
          sd.timeout.cancel
          sendMessageErrorResult(e, sd.current)
        }

        case _ =>
      }
    }

    exception.map { broadcaster ! new Device.StopDevice(id, _) }
  }

  protected def sendMessageErrorResult(exception: Exception, record: Seq[Record]): Unit =
    record.foreach(sendMessageErrorResult(exception, _))

  protected def sendMessageErrorResult(exception: Exception, record: Record): Unit =
    sendMessageErrorResult(exception, record.sendMessage, record.sendMessageSender)

  protected def sendMessageErrorResult(exception: Exception, sendMessage: Device.SendMessage, sendMessageSender: ActorRef): Unit = {
    val sendMessageErrorResult = new Device.SendMessageErrorResult(exception, sendMessage, sendMessageSender)
    sendMessageErrorResult.reply(deviceActor)
  }
}