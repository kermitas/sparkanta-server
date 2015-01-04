/*
package as.sparkanta.actor.device

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ FSM, ActorRef, Cancellable }
import as.sparkanta.device.{ DeviceIdentification, DeviceInfo }
import as.sparkanta.gateway.{ Device => DeviceSpec }
import akka.util.{ FSMSuccessOrStop, InternalMessage }
import as.akka.broadcaster.Broadcaster
import scala.net.IdentifiedConnectionInfo
import as.sparkanta.actor.message.deserializer.Deserializer
import as.sparkanta.device.message.fromdevice.{ MessageFormDevice, Ack, DeviceIdentification => DeviceIdentificationMessage }
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.actor.speedtest.SpeedTest
import scala.collection.mutable.ListBuffer
import akka.util.MessageWithSender
import as.sparkanta.actor.message.serializer.Serializer

object DeviceWorker {
  sealed trait State extends Serializable
  case object WaitingForDeviceIdentification extends State
  case object WaitingForSpeedTestResult extends State
  case object WaitingForMessageToSend extends State
  case object WaitingForTcpAck extends State
  case object WaitingForDeviceAck extends State

  sealed trait StateData extends Serializable
  case class WaitingForDeviceIdentificationStateData(timeout: Cancellable) extends StateData
  case class WaitingForSpeedTestResultStateData(timeout: Cancellable, deviceInfo: DeviceInfo) extends StateData
  case class WaitingForMessageToSendStateData(deviceInfo: DeviceInfo, messagesToSend: ListBuffer[Record]) extends StateData
  case class WaitingForTcpAckStateData(deviceInfo: DeviceInfo, current: Record, timeout: Cancellable, messagesToSend: ListBuffer[Record]) extends StateData
  case class WaitingForDeviceAckStateData(deviceInfo: DeviceInfo, current: Record, timeout: Cancellable, messagesToSend: ListBuffer[Record]) extends StateData

  object DeviceIdentificationTimeout extends InternalMessage
  object SpeedTestTimeout extends InternalMessage
  object TcpAckTimeout extends InternalMessage
  object DeviceAckTimeout extends InternalMessage

  class Record(val sendMessage: DeviceSpec.SendMessage, val sendMessageSender: ActorRef, val serializedMessage: Array[Byte])
}

class DeviceWorker(
  connectionInfo:                            IdentifiedConnectionInfo,
  akkaSocketTcpActor:                        ActorRef,
  broadcaster:                               ActorRef,
  waitingForDeviceIdentificationTimeoutInMs: Long,
  speedTestTimeAndTimeoutInMs:               Option[(Long, Long)],
  deviceActor:                               ActorRef,
  maximumQueuedSendDataMessages:             Long
)

  extends FSM[DeviceWorker.State, DeviceWorker.StateData] with FSMSuccessOrStop[DeviceWorker.State, DeviceWorker.StateData] {

  import DeviceWorker._
  import context.dispatcher

  {
    val timeout = context.system.scheduler.scheduleOnce(waitingForDeviceIdentificationTimeoutInMs millis, self, DeviceIdentificationTimeout)
    startWith(WaitingForDeviceIdentification, new WaitingForDeviceIdentificationStateData(timeout))
  }

  when(WaitingForDeviceIdentification) {
    case Event(a: Deserializer.DeserializationSuccessResult, sd: WaitingForDeviceIdentificationStateData) => successOrStopWithFailure { performDeviceIdentification(a, sd) }
    case Event(DeviceIdentificationTimeout, sd: WaitingForDeviceIdentificationStateData)                  => successOrStopWithFailure { performDeviceIdentificationTimeout(sd) }
  }

  when(WaitingForSpeedTestResult) {
    case Event(a: Deserializer.DeserializationSuccessResult, sd: WaitingForSpeedTestResultStateData) => successOrStopWithFailure { publishNewMessage(a, sd.deviceInfo) }
    case Event(a: SpeedTest.SpeedTestResult, sd: WaitingForSpeedTestResultStateData)                 => successOrStopWithFailure { receivedSpeedTestResult(a, sd) }
    case Event(SpeedTestTimeout, sd: WaitingForSpeedTestResultStateData)                             => successOrStopWithFailure { receivedSpeedTestResultTimeout(sd) }

    // TODO receive SerializationSuccessResult and send data to socket and stay
  }

  when(WaitingForMessageToSend) {
    case Event(a: Deserializer.DeserializationSuccessResult, sd: WaitingForMessageToSendStateData) => successOrStopWithFailure { publishNewMessage(a, sd.deviceInfo) }

    // TODO listen for messages to send (that means: listen for SerializationResult that as root will have SerializeWithSendMessage)
    // TODO when there is no Ack then great, send back result and stay;
    // TODO otherwise go waiting for ack, there are two types of Ack: Tcp (that means: SendDataResult) and
    case Event(a: Serializer.SerializationSuccessResult, sd: WaitingForMessageToSendStateData)     => successOrStopWithFailure { stay using sd } // TODO
  }

  when(WaitingForTcpAck) {
    case Event(a: Deserializer.DeserializationSuccessResult, sd: WaitingForTcpAckStateData) => successOrStopWithFailure { publishNewMessage(a, sd.deviceInfo) }

    case Event(a: Serializer.SerializationSuccessResult, sd: WaitingForTcpAckStateData)     => successOrStopWithFailure { queueDataToSend(a, sd.messagesToSend) }

    case Event(a: Socket.SendDataSuccessResult, sd: WaitingForTcpAckStateData)              => successOrStopWithFailure { stay using sd } // TODO reply to SendData sender and continue todo queue

    case Event(TcpAckTimeout, sd: WaitingForTcpAckStateData)                                => successOrStopWithFailure { stay using sd } // TODO
  }

  when(WaitingForDeviceAck) {

    case Event(a: Serializer.SerializationSuccessResult, sd: WaitingForDeviceAckStateData) => successOrStopWithFailure { queueDataToSend(a, sd.messagesToSend) }

    case Event(a: Socket.SendDataSuccessResult, sd: WaitingForDeviceAckStateData)          => successOrStopWithFailure { stay using sd } // TODO if there were problem with sending then just stop actor

    // TODO this is probably bad here, should hunt for Ack but also support
    //case Event(a: Deserializer.DeserializationSuccessResult, sd: WaitingForDeviceAckStateData) => successOrStopWithFailure { publishNewMessage(a, sd.deviceInfo) }

    case Event(a: Deserializer.DeserializationSuccessResult, sd: WaitingForDeviceAckStateData) if a.deserializedMessageFromDevice.get.isInstanceOf[Ack] =>
      successOrStopWithFailure { stay using sd } // TODO received ACK message should have ack message code as this what we waited for, reply to SendData sender, publish it and continue todo queue

    case Event(DeviceAckTimeout, sd: WaitingForDeviceAckStateData) => successOrStopWithFailure { stay using sd } // TODO
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(a: Socket.SendDataSuccessResult, stateData)    => stay using stateData
    case Event(a: Socket.SendDataErrorResult, stateData)      => stop(FSM.Failure(new Exception("Stopping because of problem with sending data.", a.exception.get)))

    case Event(a: DeviceSpec.DisconnectDevice, stateData)     => successOrStopWithFailure { disconnectDevice(a.cause) }
    case Event(a: DeviceSpec.DisconnectAllDevices, stateData) => successOrStopWithFailure { disconnectAllDevices(a.cause) }

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
    broadcaster ! new Broadcaster.Register(self, new DeviceWorkerClassifier(connectionInfo.remote.id, broadcaster))
  }

  protected def performDeviceIdentification(deserializationSuccessResult: Deserializer.DeserializationSuccessResult, sd: WaitingForDeviceIdentificationStateData): State =
    performDeviceIdentification(deserializationSuccessResult.deserializedMessageFromDevice.get, sd)

  protected def performDeviceIdentification(messageFromDevice: MessageFormDevice, sd: WaitingForDeviceIdentificationStateData): State = messageFromDevice match {
    case a: DeviceIdentificationMessage => performDeviceIdentification(a, sd)
    case u                              => throw new Exception(s"First message should be ${classOf[DeviceIdentificationMessage].getSimpleName}.")
  }

  protected def performDeviceIdentification(deviceIdentification: DeviceIdentificationMessage, sd: WaitingForDeviceIdentificationStateData): State = {
    sd.timeout.cancel

    // TODO in future: go to state that checks if device of this unique ID (and unique name?) is not currently online

    speedTestTimeAndTimeoutInMs match {
      case Some((speedTestTimeInMs, speedTestTimeoutInMs)) => {
        broadcaster ! new SpeedTest.StartSpeedTest(connectionInfo.remote.id, speedTestTimeInMs)
        val timeout = context.system.scheduler.scheduleOnce(speedTestTimeoutInMs millis, self, SpeedTestTimeout)
        val deviceInfo = new DeviceInfo(connectionInfo, deviceIdentification, None)
        goto(WaitingForSpeedTestResult) using new WaitingForSpeedTestResultStateData(timeout, deviceInfo)
      }

      case None => gotoWaitingForMessageToSend(deviceIdentification, None)
    }
  }

  protected def performDeviceIdentificationTimeout(sd: WaitingForDeviceIdentificationStateData) =
    throw new Exception(s"Device identification timeout ($waitingForDeviceIdentificationTimeoutInMs milliseconds).")

  protected def receivedSpeedTestResult(speedTestResult: SpeedTest.SpeedTestResult, sd: WaitingForSpeedTestResultStateData) = {
    sd.timeout.cancel
    gotoWaitingForMessageToSend(sd.deviceInfo.deviceIdentification, Some((speedTestResult.pingPongsCount.get, speedTestTimeAndTimeoutInMs.get._1)))
  }

  protected def receivedSpeedTestResultTimeout(sd: WaitingForSpeedTestResultStateData) =
    throw new Exception(s"Speed test result timeout (${speedTestTimeAndTimeoutInMs.get._2} milliseconds).")

  protected def gotoWaitingForMessageToSend(deviceIdentification: DeviceIdentification, pingPongsCountInTimeInMs: Option[(Long, Long)]) = {
    val deviceInfo = new DeviceInfo(connectionInfo, deviceIdentification, pingPongsCountInTimeInMs)
    broadcaster ! new DeviceSpec.DeviceIsUp(deviceInfo)
    goto(WaitingForMessageToSend) using new WaitingForMessageToSendStateData(deviceInfo, ListBuffer.empty)
  }

  protected def disconnectDevice(cause: Exception) = throw new Exception(s"Disconnecting because of ${classOf[DeviceSpec.DisconnectDevice].getSimpleName}.", cause)

  protected def disconnectAllDevices(cause: Exception) = throw new Exception(s"Disconnecting because of ${classOf[DeviceSpec.DisconnectAllDevices].getSimpleName}.", cause)

  protected def publishNewMessage(deserializationSuccessResult: Deserializer.DeserializationSuccessResult, deviceInfo: DeviceInfo): State =
    publishNewMessage(deserializationSuccessResult.deserializedMessageFromDevice.get, deviceInfo)

  protected def publishNewMessage(messageFormDevice: MessageFormDevice, deviceInfo: DeviceInfo): State = {
    broadcaster ! new DeviceSpec.NewMessage(deviceInfo, messageFormDevice)
    stay using stateData
  }

  protected def queueDataToSend(serializationSuccessResult: Serializer.SerializationSuccessResult, messagesToSend: ListBuffer[Record]): State = {
    val serializeWithSendMessage = serializationSuccessResult.request1.message.asInstanceOf[SerializeWithSendMessage]
    queueDataToSend(serializeWithSendMessage.sendMessage, serializationSuccessResult.request1.messageSender, serializationSuccessResult.serializedMessageToDevice.get, messagesToSend)
  }

  protected def queueDataToSend(sendMessage: DeviceSpec.SendMessage, sendMessageSender: ActorRef, serializedMessage: Array[Byte], messagesToSend: ListBuffer[Record]): State =
    queueDataToSend(new Record(sendMessage, sendMessageSender, serializedMessage), messagesToSend)

  protected def queueDataToSend(record: Record, messagesToSend: ListBuffer[Record]): State = {
    messagesToSend += record

    if (messagesToSend.size >= maximumQueuedSendDataMessages) {
      stop(FSM.Failure(new Exception(s"Maximum number of $maximumQueuedSendDataMessages queued messages to send reached.")))
    } else {
      stay using stateData
    }
  }

  protected def terminate(reason: FSM.Reason, currentState: DeviceWorker.State, stateData: DeviceWorker.StateData): Unit = {

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
          case u            => new Exception(s"${getClass.getSimpleName} actor was stopped, cause type ${u.getClass.getSimpleName}, cause $u.")
        }
      }
    }

    broadcaster ! new Socket.StopListeningAt(connectionInfo.remote.id)

    stateData match {
      case sd: WaitingForDeviceIdentificationStateData => {
        sd.timeout.cancel
      }

      case sd: WaitingForSpeedTestResultStateData => {
        sd.timeout.cancel
      }

      case sd: WaitingForMessageToSendStateData => {
        sendDeviceIsDown(sd.deviceInfo, exception)
      }

      case sd: WaitingForTcpAckStateData => {
        sd.timeout.cancel

        sendSendMessageErrorResult(sd.current, exception)
        sendSendMessageErrorResult(sd.messagesToSend, exception)

        sendDeviceIsDown(sd.deviceInfo, exception)
      }

      case sd: WaitingForDeviceAckStateData => {
        sd.timeout.cancel

        sendSendMessageErrorResult(sd.current, exception)
        sendSendMessageErrorResult(sd.messagesToSend, exception)

        sendDeviceIsDown(sd.deviceInfo, exception)
      }
    }
  }

  protected def sendSendMessageErrorResult(messagesToSend: Seq[Record], exception: Exception): Unit =
    messagesToSend.foreach(sendSendMessageErrorResult(_, exception))

  protected def sendSendMessageErrorResult(record: Record, exception: Exception): Unit =
    sendSendMessageErrorResult(record.sendMessage, record.sendMessageSender, exception)

  protected def sendSendMessageErrorResult(sendMessage: DeviceSpec.SendMessage, sendMessageSender: ActorRef, exception: Exception): Unit = {
    val sendMessageErrorResult = new DeviceSpec.SendMessageErrorResult(exception, sendMessage, sendMessageSender)
    sendMessageErrorResult.reply(deviceActor)
  }

  protected def sendDeviceIsDown(deviceInfo: DeviceInfo, exception: Exception): Unit =
    broadcaster ! new DeviceSpec.DeviceIsDown(deviceInfo, exception, deviceInfo.timeInSystemInMillis)
}
*/ 