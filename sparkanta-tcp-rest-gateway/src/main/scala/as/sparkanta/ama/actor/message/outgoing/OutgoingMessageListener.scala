package as.sparkanta.ama.actor.message.outgoing

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ OneForOneStrategy, SupervisorStrategy, FSM }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.device.message.serialize.Serializer
import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker, Disconnect, MessageLengthHeader }
import as.sparkanta.gateway.message.{ DataToDeviceSendConfirmation, DataToDevice }
import as.sparkanta.server.message.MessageToDevice
import akka.util.FSMSuccessOrStop

object OutgoingMessageListener {
  sealed trait State extends Serializable
  case object WaitingForMessageToSend extends State
  case object DisconnectingDevice extends State

  sealed trait StateData extends Serializable
  case object WaitingForMessageToSendStateData extends StateData
  case class DisconnectingDeviceStateData(disconnectDataToDevice: DataToDevice) extends StateData

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  object WaitingForAckAfterSendingDisconnectTimeout extends InternalMessage
}

class OutgoingMessageListener(
  val amaConfig:           AmaConfig,
  val config:              OutgoingMessageListenerConfig,
  val runtimeId:           Long,
  val serializer:          Serializer[MessageToDeviceMarker],
  val messageLengthHeader: MessageLengthHeader
) extends FSM[OutgoingMessageListener.State, OutgoingMessageListener.StateData] with FSMSuccessOrStop[OutgoingMessageListener.State, OutgoingMessageListener.StateData] {

  def this(
    amaConfig:           AmaConfig,
    runtimeId:           Long,
    serializer:          Serializer[MessageToDeviceMarker],
    messageLengthHeader: MessageLengthHeader
  ) = this(amaConfig, OutgoingMessageListenerConfig.fromTopKey(amaConfig.config), runtimeId, serializer, messageLengthHeader)

  import OutgoingMessageListener._

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Terminating because once of child actors failed.", t)))
      SupervisorStrategy.Escalate
    }
  }

  startWith(WaitingForMessageToSend, WaitingForMessageToSendStateData)

  when(WaitingForMessageToSend) {
    case Event(mtd: MessageToDevice, WaitingForMessageToSendStateData) => successOrStopWithFailure { serializeMessageToDevice(mtd.messageToDevice) }
  }

  when(DisconnectingDevice) {
    case Event(dtdsc: DataToDeviceSendConfirmation, sd: DisconnectingDeviceStateData)        => successOrStopWithFailure { receivedDataToDeviceSendConfirmationWhileDisconnectingDevice(dtdsc, sd) }

    case Event(WaitingForAckAfterSendingDisconnectTimeout, sd: DisconnectingDeviceStateData) => stop(FSM.Failure(new Exception(s"Timeout (${config.waitingForAckAfterSendingDisconnectTimeoutInSeconds} seconds) while waiting for confirmation after sending ${classOf[Disconnect].getSimpleName} message.")))
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(_: DataToDeviceSendConfirmation, stateData) => stay using stateData

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
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new OutgoingMessageListenerClassifier(runtimeId))
  }

  protected def serializeMessageToDevice(messageToDevice: MessageToDeviceMarker) = {

    val messageToDevicePrefixedWithLengthHeader = {
      val messageToDeviceAsBytes = serializer.serialize(messageToDevice)
      messageLengthHeader.prepareMessageToGo(messageToDeviceAsBytes)
    }

    val dataToDevice = new DataToDevice(runtimeId, messageToDevicePrefixedWithLengthHeader)
    amaConfig.broadcaster ! dataToDevice

    messageToDevice match {
      case dd: Disconnect => {
        context.system.scheduler.scheduleOnce(config.waitingForAckAfterSendingDisconnectTimeoutInSeconds seconds, self, WaitingForAckAfterSendingDisconnectTimeout)(context.dispatcher)
        goto(DisconnectingDevice) using new DisconnectingDeviceStateData(dataToDevice)
      }

      case messageToDevice => stay using WaitingForMessageToSendStateData
    }
  }

  protected def receivedDataToDeviceSendConfirmationWhileDisconnectingDevice(dtdsc: DataToDeviceSendConfirmation, sd: DisconnectingDeviceStateData) = if (dtdsc.successfullySendDataToDevice == sd.disconnectDataToDevice) {
    log.debug(s"Stopping because received send confirmation of ${classOf[Disconnect].getSimpleName} message.")
    stop(FSM.Normal)
  } else {
    stay using sd
  }

  protected def terminate(reason: FSM.Reason, currentState: OutgoingMessageListener.State, stateData: OutgoingMessageListener.StateData) = reason match {

    case FSM.Normal => {
      log.debug(s"Stopping (normal), state $currentState, data $stateData, runtimeId $runtimeId.")
    }

    case FSM.Shutdown => {
      log.debug(s"Stopping (shutdown), state $currentState, data $stateData, runtimeId $runtimeId.")
    }

    case FSM.Failure(cause) => {
      log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData, runtimeId $runtimeId.")
    }
  }
}
