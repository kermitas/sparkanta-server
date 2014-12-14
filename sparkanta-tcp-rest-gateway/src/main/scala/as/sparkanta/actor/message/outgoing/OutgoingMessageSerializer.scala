package as.sparkanta.actor.message.outgoing

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ OneForOneStrategy, SupervisorStrategy, FSM }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.device.message.serialize.Serializer
import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker, Disconnect }
import as.sparkanta.device.message.length.MessageLengthHeaderCreator
import as.sparkanta.gateway.message.{ DataToDeviceSendConfirmation, DataToDevice }
import as.sparkanta.server.message.MessageToDevice
import akka.util.FSMSuccessOrStop

object OutgoingMessageSerializer {
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

class OutgoingMessageSerializer(
  val amaConfig:                  AmaConfig,
  val config:                     OutgoingMessageSerializerConfig,
  val remoteAddressId:            Long,
  val serializer:                 Serializer[MessageToDeviceMarker],
  val messageLengthHeaderCreator: MessageLengthHeaderCreator
) extends FSM[OutgoingMessageSerializer.State, OutgoingMessageSerializer.StateData] with FSMSuccessOrStop[OutgoingMessageSerializer.State, OutgoingMessageSerializer.StateData] {

  def this(
    amaConfig:                  AmaConfig,
    remoteAddressId:            Long,
    serializer:                 Serializer[MessageToDeviceMarker],
    messageLengthHeaderCreator: MessageLengthHeaderCreator
  ) = this(amaConfig, OutgoingMessageSerializerConfig.fromTopKey(amaConfig.config), remoteAddressId, serializer, messageLengthHeaderCreator)

  import OutgoingMessageSerializer._

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Terminating because once of child actors failed.", t)))
      SupervisorStrategy.Escalate
    }
  }

  startWith(WaitingForMessageToSend, WaitingForMessageToSendStateData)

  when(WaitingForMessageToSend) {
    case Event(mtd: MessageToDevice, WaitingForMessageToSendStateData) => successOrStopWithFailure { serializeMessageToDevice(mtd.messageToDevice) }

    case Event(_: DataToDeviceSendConfirmation, stateData)             => stay using stateData
  }

  when(DisconnectingDevice) {
    case Event(dtdsc: DataToDeviceSendConfirmation, sd: DisconnectingDeviceStateData)        => successOrStopWithFailure { receivedDataToDeviceSendConfirmationWhileDisconnectingDevice(dtdsc, sd) }

    case Event(WaitingForAckAfterSendingDisconnectTimeout, sd: DisconnectingDeviceStateData) => stop(FSM.Failure(new Exception(s"Timeout (${config.waitingForAckAfterSendingDisconnectTimeoutInSeconds} seconds) while waiting for ${classOf[DataToDeviceSendConfirmation].getSimpleName} after sending ${classOf[Disconnect].getSimpleName} message.")))
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
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
    amaConfig.broadcaster ! new Broadcaster.Register(self, new OutgoingMessageSerializerClassifier(remoteAddressId))
  }

  protected def serializeMessageToDevice(messageToDevice: MessageToDeviceMarker) = {

    val dataToDevice = {
      val messageToDeviceAsByteArray = serializer.serialize(messageToDevice)
      val messageLengthHeader = messageLengthHeaderCreator.prepareMessageLengthHeader(messageToDeviceAsByteArray.length)
      new DataToDevice(remoteAddressId, messageLengthHeader, messageToDeviceAsByteArray)
    }

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
    log.debug(s"Stopping because received send confirmation of successful sending of ${classOf[Disconnect].getSimpleName} message.")
    stop(FSM.Normal)
  } else {
    stay using sd
  }

  protected def terminate(reason: FSM.Reason, currentState: OutgoingMessageSerializer.State, stateData: OutgoingMessageSerializer.StateData) = reason match {

    case FSM.Normal => {
      log.debug(s"Stopping (normal), state $currentState, data $stateData, remoteAddressId $remoteAddressId.")
    }

    case FSM.Shutdown => {
      log.debug(s"Stopping (shutdown), state $currentState, data $stateData, remoteAddressId $remoteAddressId.")
    }

    case FSM.Failure(cause) => {
      log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData, remoteAddressId $remoteAddressId.")
    }
  }
}
