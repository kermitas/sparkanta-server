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
  case class WaitingForSpeedTestResultStateData(timeout: Cancellable, deviceIdentification: DeviceIdentification) extends StateData
  case class WaitingForMessageToSendStateData(deviceInfo: DeviceInfo, messagesToSend: ListBuffer[MessageWithSender[DeviceSpec.SendMessage]]) extends StateData
  case class WaitingForTcpAckStateData(deviceInfo: DeviceInfo, current: MessageWithSender[DeviceSpec.SendMessage], timeout: Cancellable, messagesToSend: ListBuffer[MessageWithSender[DeviceSpec.SendMessage]]) extends StateData
  case class WaitingForDeviceAckStateData(deviceInfo: DeviceInfo, current: MessageWithSender[DeviceSpec.SendMessage], timeout: Cancellable, messagesToSend: ListBuffer[MessageWithSender[DeviceSpec.SendMessage]]) extends StateData

  object DeviceIdentificationTimeout extends InternalMessage
  object SpeedTestTimeout extends InternalMessage
  object AckTimeout extends InternalMessage
}

class DeviceWorker(
  connectionInfo:                            IdentifiedConnectionInfo,
  akkaSocketTcpActor:                        ActorRef,
  broadcaster:                               ActorRef,
  waitingForDeviceIdentificationTimeoutInMs: Long,
  speedTestTimeInMs:                         Option[Long],
  deviceActor:                               ActorRef
)

  extends FSM[DeviceWorker.State, DeviceWorker.StateData] with FSMSuccessOrStop[DeviceWorker.State, DeviceWorker.StateData] {

  import DeviceWorker._
  import context.dispatcher

  {
    val timeout = context.system.scheduler.scheduleOnce(waitingForDeviceIdentificationTimeoutInMs millis, self, DeviceIdentificationTimeout)
    startWith(WaitingForDeviceIdentification, new WaitingForDeviceIdentificationStateData(timeout))
  }

  when(WaitingForDeviceIdentification) {
    case Event(a: Deserializer.DeserializationSuccessResult, sd: WaitingForDeviceIdentificationStateData) =>
      successOrStopWithFailure { deviceIdentification(a.deserializedMessageFromDevice.get, sd) } // TODO received message should be exactly DeviceIdentificationMessage

    case Event(DeviceIdentificationTimeout, sd: WaitingForDeviceIdentificationStateData) => successOrStopWithFailure { stay using sd }
  }

  when(WaitingForSpeedTestResult) {
    case Event(a: SpeedTest.SpeedTestResult, sd: WaitingForSpeedTestResultStateData) => successOrStopWithFailure { stay using sd } // TODO
    case Event(SpeedTestTimeout, sd: WaitingForSpeedTestResultStateData)             => successOrStopWithFailure { stay using sd } // TODO
  }

  when(WaitingForMessageToSend) {
    // TODO listen for messages to send (that means: listen for SerializationResult that as root will have SerializeWithSendMessage)
    // TODO when there is no Ack then great, send back result and stay;
    // TODO otherwise go waiting for ack, there are two types of Ack: Tcp (that means: SendDataResult) and
    case Event(a: Serializer.SerializationSuccessResult, sd: WaitingForMessageToSendStateData) => successOrStopWithFailure { stay using sd } // TODO
  }

  when(WaitingForTcpAck) {

    // TODO on any message to send - buffer it (and of course check buffer size)

    case Event(a: Socket.SendDataResult, sd: WaitingForTcpAckStateData) => successOrStopWithFailure { stay using sd } // TODO reply to SendData sender and continue todo queue

    case Event(AckTimeout, sd: WaitingForTcpAckStateData)               => successOrStopWithFailure { stay using sd } // TODO
  }

  when(WaitingForDeviceAck) {

    // TODO on any message to send - buffer it (and of course check buffer size)

    case Event(a: Socket.SendDataResult, sd: WaitingForTcpAckStateData) => successOrStopWithFailure { stay using sd } // TODO if there were problem with sending then just stop actor

    case Event(a: Deserializer.DeserializationSuccessResult, sd: WaitingForDeviceAckStateData) if a.deserializedMessageFromDevice.get.isInstanceOf[Ack] =>
      successOrStopWithFailure { stay using sd } // TODO received ACK message should have ack message code as this what we waited for, reply to SendData sender, publish it and continue todo queue

    case Event(AckTimeout, sd: WaitingForDeviceAckStateData) => successOrStopWithFailure { stay using sd } // TODO
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(a: Deserializer.DeserializationSuccessResult, stateData) => successOrStopWithFailure { stay using stateData } // TODO publish that new message that comes
    case Event(a: DeviceSpec.DisconnectDevice, stateData)               => successOrStopWithFailure { stay using stateData } // TODO
    case Event(DeviceSpec.DisconnectAllDevices, stateData)              => successOrStopWithFailure { stay using stateData } // TODO

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

  protected def sendSendMessageErrorResult(messagesToSend: Seq[MessageWithSender[DeviceSpec.SendMessage]], exception: Exception): Unit =
    messagesToSend.foreach(sendSendMessageErrorResult(_, exception))

  protected def sendSendMessageErrorResult(mws: MessageWithSender[DeviceSpec.SendMessage], exception: Exception): Unit =
    sendSendMessageErrorResult(mws.message, mws.messageSender, exception)

  protected def sendSendMessageErrorResult(sendMessage: DeviceSpec.SendMessage, sendMessageSender: ActorRef, exception: Exception): Unit = {
    val sendMessageErrorResult = new DeviceSpec.SendMessageErrorResult(exception, sendMessage, sendMessageSender)
    sendMessageErrorResult.reply(deviceActor)
  }

  protected def sendDeviceIsDown(deviceInfo: DeviceInfo, exception: Exception): Unit =
    broadcaster ! new DeviceSpec.DeviceIsDown(deviceInfo, exception, deviceInfo.timeInSystemInMillis)

  protected def deviceIdentification(messageFromDevice: MessageFormDevice, sd: WaitingForDeviceIdentificationStateData): State =
    deviceIdentification(messageFromDevice.asInstanceOf[DeviceIdentificationMessage], sd)

  protected def deviceIdentification(deviceIdentification: DeviceIdentificationMessage, sd: WaitingForDeviceIdentificationStateData): State = {
    sd.timeout.cancel

    speedTestTimeInMs match {
      case Some(speedTestTimeInMs) => {
        broadcaster ! new SpeedTest.StartSpeedTest(connectionInfo.remote.id, speedTestTimeInMs)
        val timeout = context.system.scheduler.scheduleOnce(speedTestTimeInMs + 250 millis, self, SpeedTestTimeout)
        goto(WaitingForSpeedTestResult) using new WaitingForSpeedTestResultStateData(timeout, deviceIdentification)
      }

      case None => gotoWaitingForMessageToSend(deviceIdentification, None)
    }
  }

  protected def gotoWaitingForMessageToSend(deviceIdentification: DeviceIdentification, pingPongCountPerSecond: Option[Long]) = {
    val deviceInfo = new DeviceInfo(connectionInfo, deviceIdentification, pingPongCountPerSecond)
    broadcaster ! new DeviceSpec.DeviceIsUp(deviceInfo)
    goto(WaitingForMessageToSend) using new WaitingForMessageToSendStateData(deviceInfo, ListBuffer.empty)
  }
}
