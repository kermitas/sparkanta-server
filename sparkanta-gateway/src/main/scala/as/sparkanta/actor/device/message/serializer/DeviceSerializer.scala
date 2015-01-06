package as.sparkanta.actor.device.message.serializer

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, Cancellable, FSM, ActorRefFactory, Props }
import akka.util.{ FSMSuccessOrStop, InternalMessage }
import as.sparkanta.gateway.{ Device, DeviceAck, NoAck, TcpAck }
import as.sparkanta.device.message.todevice.{ NoAck => DeviceNoAck }
import scala.collection.mutable.ListBuffer
import as.akka.broadcaster.Broadcaster
import as.sparkanta.device.message.fromdevice.Ack
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.device.message.serialize.Serializers

object DeviceSerializer {

  lazy final val waitingForSendDataResultTimeoutInMsIfNoAck = 2 * 1000

  sealed trait State extends Serializable
  case object WaitingForDataToSend extends State
  case object WaitingForSendDataResult extends State
  case object WaitingForDeviceAck extends State

  sealed trait StateData extends Serializable
  case object WaitingForDataToSendStateData extends StateData
  case class WaitingForSendDataResultStateData(current: Record, timeout: Cancellable, timeoutInMs: Long) extends StateData
  case class WaitingForDeviceAckStateData(current: Record, timeout: Cancellable, timeoutInMs: Long) extends StateData

  object Timeout extends InternalMessage

  class Record(val sendMessage: Device.SendMessage, val sendMessageSender: ActorRef)

  def startActor(actorRefFactory: ActorRefFactory, id: Long, broadcaster: ActorRef, deviceActor: ActorRef, maximumQueuedSendDataMessages: Long): ActorRef = {
    val props = Props(new DeviceSerializer(id, broadcaster, deviceActor, maximumQueuedSendDataMessages))
    val actor = actorRefFactory.actorOf(props, name = classOf[DeviceSerializer].getSimpleName + "-" + id)
    broadcaster ! new Broadcaster.Register(actor, new DeviceSerializerClassifier(id, broadcaster))
    actor
  }
}

class DeviceSerializer(id: Long, broadcaster: ActorRef, var deviceActor: ActorRef, maximumQueuedSendDataMessages: Long)
  extends FSM[DeviceSerializer.State, DeviceSerializer.StateData] with FSMSuccessOrStop[DeviceSerializer.State, DeviceSerializer.StateData] {

  import DeviceSerializer._
  import context.dispatcher

  protected val serializers = new Serializers
  protected val buffer = new ListBuffer[Record]

  startWith(WaitingForDataToSend, WaitingForDataToSendStateData)

  when(WaitingForDataToSend) {
    case Event(a: Device.SendMessage, _) => successOrStopWithFailure { sendMessage(a, sender) }
  }

  when(WaitingForSendDataResult) {
    case Event(_: Socket.SendDataSuccessResult, sd: WaitingForSendDataResultStateData) => successOrStopWithFailure { sendDataSuccess(sd) }
    case Event(a: Device.SendMessage, _) => successOrStopWithFailure { bufferSerializationSuccess(a, sender) }
    case Event(a: Socket.SendDataErrorResult, _) => successOrStopWithFailure { sendDataError(a) }
    case Event(Timeout, sd: WaitingForSendDataResultStateData) => successOrStopWithFailure { timeout(sd) }
  }

  when(WaitingForDeviceAck) {
    case Event(a: Ack, sd: WaitingForDeviceAckStateData)  => successOrStopWithFailure { deviceAck(a, sd) }
    case Event(_: Socket.SendDataSuccessResult, sd)       => stay using stateData
    case Event(a: Device.SendMessage, _)                  => successOrStopWithFailure { bufferSerializationSuccess(a, sender) }
    case Event(a: Socket.SendDataErrorResult, _)          => successOrStopWithFailure { sendDataError(a) }
    case Event(Timeout, sd: WaitingForDeviceAckStateData) => successOrStopWithFailure { timeout(sd) }
  }

  onTransition {
    case fromState -> toState => log.debug(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(a: Device.SendMessage, _)        => successOrStopWithFailure { sendMessage(a, sender) }
    case Event(a: Device.StartErrorResult, _)   => successOrStopWithFailure { deviceStartError }
    case Event(a: Device.StartSuccessResult, _) => successOrStopWithFailure { deviceStartSuccess(sender) }
    case Event(a: Device.Stopped, _)            => successOrStopWithFailure { deviceStopped }
    case Event(_: Ack, _)                       => stay using stateData

    case Event(unknownMessage, stateData) => {
      log.warning(s"Received unknown message '$unknownMessage' in state $stateName (state data $stateData)")
      stay using stateData
    }
  }

  onTermination {
    case StopEvent(reason, currentState, stateData) => terminate(reason, currentState, stateData)
  }

  initialize

  protected def sendMessage(sendMessageMessage: Device.SendMessage, sendMessageSender: ActorRef): State = try {
    val ack = if (sendMessageMessage.ack.isInstanceOf[DeviceAck]) sendMessageMessage.ack.asInstanceOf[DeviceAck].deviceAck else DeviceNoAck
    val serializedMessageToDevice = serializers.serialize(sendMessageMessage.messageToDevice, ack)

    sendMessage(serializedMessageToDevice, sendMessageMessage, sendMessageSender)
  } catch {
    case exception: Exception => {
      buffer += new Record(sendMessageMessage, sendMessageSender)
      val e = new Exception("Message to device serialization problem.", exception)
      stop(FSM.Failure(e))
    }
  }

  protected def deviceStartError = stop(FSM.Normal)

  protected def deviceStartSuccess(startSuccessResultSender: ActorRef) = {
    deviceActor = startSuccessResultSender
    stay using stateData
  }

  protected def deviceStopped = stop(FSM.Normal)

  protected def sendMessage(serializedMessageToDevice: Array[Byte], sendMessageMessage: Device.SendMessage, sendMessageSender: ActorRef): State = sendMessageMessage.ack match {
    case NoAck => {
      broadcaster ! new Socket.SendData(serializedMessageToDevice, id, NoAck)
      val timeout = context.system.scheduler.scheduleOnce(waitingForSendDataResultTimeoutInMsIfNoAck millis, self, Timeout)
      val record = new Record(sendMessageMessage, sendMessageSender)
      goto(WaitingForSendDataResult) using new WaitingForSendDataResultStateData(record, timeout, waitingForSendDataResultTimeoutInMsIfNoAck)
    }

    case tcpAck: TcpAck => {
      broadcaster ! new Socket.SendData(serializedMessageToDevice, id, tcpAck)
      val timeout = context.system.scheduler.scheduleOnce(tcpAck.timeoutInMillis millis, self, Timeout)
      val record = new Record(sendMessageMessage, sendMessageSender)
      goto(WaitingForSendDataResult) using new WaitingForSendDataResultStateData(record, timeout, tcpAck.timeoutInMillis)
    }

    case deviceAck: DeviceAck => {
      broadcaster ! new Socket.SendData(serializedMessageToDevice, id, NoAck)
      val timeout = context.system.scheduler.scheduleOnce(deviceAck.timeoutInMillis millis, self, Timeout)
      val record = new Record(sendMessageMessage, sendMessageSender)
      goto(WaitingForDeviceAck) using new WaitingForDeviceAckStateData(record, timeout, deviceAck.timeoutInMillis)
    }
  }

  protected def timeout(sd: WaitingForSendDataResultStateData) = stop(FSM.Failure(new Exception(s"Timeout (${sd.timeoutInMs} milliseconds) while waiting for send data confirmation reached.")))

  protected def timeout(sd: WaitingForDeviceAckStateData) = stop(FSM.Failure(new Exception(s"Timeout (${sd.timeoutInMs} milliseconds) while waiting for device ack reached.")))

  protected def bufferSerializationSuccess(sendMessageMessage: Device.SendMessage, sendMessageSender: ActorRef): State = {
    buffer += new Record(sendMessageMessage, sendMessageSender)

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
    //stay using sd
    stop(FSM.Failure(new Exception(s"Received ack message code ${ack.ackedMessageCode} does not match ${sd.current.sendMessage.messageToDevice.messageCode} (that is required).")))
  }

  protected def pickUpNextTaskOrGoToInitialState = if (buffer.nonEmpty) {

    def sendNextDataToSend = {
      val record = buffer.head
      buffer -= record
      sendMessage(record.sendMessage, record.sendMessageSender)
    }

    var nextState = sendNextDataToSend

    while (buffer.nonEmpty && nextState.stateName == WaitingForDataToSend) nextState = sendNextDataToSend

    nextState

  } else {
    goto(WaitingForDataToSend) using WaitingForDataToSendStateData
  }

  protected def terminate(reason: FSM.Reason, currentState: DeviceSerializer.State, stateData: DeviceSerializer.StateData): Unit = {

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