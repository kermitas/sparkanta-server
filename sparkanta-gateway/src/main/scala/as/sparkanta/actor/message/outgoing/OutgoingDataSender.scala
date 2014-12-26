/*
package as.sparkanta.actor.message.outgoing

import as.sparkanta.gateway.SoftwareAndHardwareIdentifiedDeviceInfo

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, FSM, Cancellable, OneForOneStrategy, SupervisorStrategy, Props, Terminated }
import akka.io.Tcp
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import scala.collection.mutable.ListBuffer
import akka.util.{ FSMSuccessOrStop, ByteString }
import as.sparkanta.gateway.message.DataToDevice
import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }
import as.sparkanta.device.message.length.MessageLengthHeaderCreator
import as.sparkanta.device.message.serialize.Serializer

object OutgoingDataSender {
  sealed trait State extends Serializable
  case object WaitingForDataToSend extends State
  case object WaitingForAck extends State

  sealed trait StateData extends Serializable
  case object WaitingForDataToSendStateData extends StateData
  case class WaitingForAckStateData(outgoingBuffer: ListBuffer[(DataToDevice, ActorRef)], waitingForAckTimeout: Cancellable, currentlySendingDataToDevice: DataToDevice, senderOfDataToDevice: ActorRef) extends StateData

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  object Ack extends InternalMessage with Tcp.Event
  object AckTimeout extends InternalMessage
}

class OutgoingDataSender(
  amaConfig:                  AmaConfig,
  config:                     OutgoingDataSenderConfig,
  deviceInfo:                 SoftwareAndHardwareIdentifiedDeviceInfo,
  tcpActor:                   ActorRef,
  messageLengthHeaderCreator: MessageLengthHeaderCreator,
  serializer:                 Serializer[MessageToDeviceMarker]
) extends FSM[OutgoingDataSender.State, OutgoingDataSender.StateData] with FSMSuccessOrStop[OutgoingDataSender.State, OutgoingDataSender.StateData] {

  def this(
    amaConfig:                               AmaConfig,
    softwareAndHardwareIdentifiedDeviceInfo: SoftwareAndHardwareIdentifiedDeviceInfo,
    tcpActor:                                ActorRef,
    deviceInfo:                              MessageLengthHeaderCreator,
    serializer:                              Serializer[MessageToDeviceMarker]
  ) = this(amaConfig, OutgoingDataSenderConfig.fromTopKey(amaConfig.config), softwareAndHardwareIdentifiedDeviceInfo, tcpActor, deviceInfo, serializer)

  import OutgoingDataSender._

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Terminating because once of child actors failed.", t)))
      SupervisorStrategy.Escalate
    }
  }

  startWith(WaitingForDataToSend, WaitingForDataToSendStateData)

  when(WaitingForDataToSend) {
    case Event(dataToDevice: DataToDevice, WaitingForDataToSendStateData) => successOrStopWithFailure { sendRequestWhileNothingToDo(dataToDevice, sender()) }
  }

  when(WaitingForAck) {
    case Event(Ack, sd: WaitingForAckStateData)                        => successOrStopWithFailure { ackReceived(sd) }

    case Event(dataToDevice: DataToDevice, sd: WaitingForAckStateData) => successOrStopWithFailure { sendRequestWhileWaitingForAck(dataToDevice, sender(), sd) }

    case Event(AckTimeout, sd: WaitingForAckStateData)                 => successOrStopWithFailure { throw new Exception(s"No ACK for more than ${config.waitingForAckTimeoutInSeconds} seconds, closing connection.") }
  }

  onTransition {
    case fromState -> toState => log.debug(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(Tcp.CommandFailed(_), stateData)         => { stop(FSM.Failure(new Exception("Write request failed."))) }

    case Event(Terminated(diedWatchedActor), stateData) => stop(FSM.Failure(s"Stopping (deviceInfo $deviceInfo) because watched actor $diedWatchedActor died."))

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
    amaConfig.broadcaster ! new Broadcaster.Register(self, new OutgoingDataSenderClassifier(deviceInfo.remoteAddress.id))

    val props = Props(new OutgoingMessageSerializer(amaConfig, deviceInfo.remoteAddress.id, serializer, messageLengthHeaderCreator))
    val outgoingMessageSerializer = context.actorOf(props, name = classOf[OutgoingMessageSerializer].getSimpleName + "-" + deviceInfo.remoteAddress.id)
    context.watch(outgoingMessageSerializer)
  }

  protected def sendRequestWhileNothingToDo(dataToDevice: DataToDevice, senderOfDataToDevice: ActorRef) = {
    val waitingForAckTimeout = sendToWire(dataToDevice.data)
    goto(WaitingForAck) using new WaitingForAckStateData(new ListBuffer[(DataToDevice, ActorRef)], waitingForAckTimeout, dataToDevice, senderOfDataToDevice)
  }

  protected def sendRequestWhileWaitingForAck(dataToDevice: DataToDevice, senderOfDataToDevice: ActorRef, sd: WaitingForAckStateData) = if (sd.outgoingBuffer.size > config.maximumNumberOfBufferedMessages) {
    stop(FSM.Failure(new Exception(s"Maximum number of ${config.maximumNumberOfBufferedMessages} buffered messages to send reached.")))
  } else {
    sd.outgoingBuffer += Tuple2(dataToDevice, senderOfDataToDevice)
    stay using sd
  }

  protected def ackReceived(sd: WaitingForAckStateData) = {
    sd.waitingForAckTimeout.cancel

    sd.currentlySendingDataToDevice.ack.map { ack => sd.senderOfDataToDevice ! ack }

    if (sd.outgoingBuffer.isEmpty) {
      goto(WaitingForDataToSend) using WaitingForDataToSendStateData
    } else {
      val dataToDeviceAndSender = sd.outgoingBuffer.head
      sd.outgoingBuffer -= dataToDeviceAndSender

      val waitingForAckTimeout = sendToWire(dataToDeviceAndSender._1.data)

      stay using sd.copy(waitingForAckTimeout = waitingForAckTimeout, currentlySendingDataToDevice = dataToDeviceAndSender._1, senderOfDataToDevice = dataToDeviceAndSender._2)
    }
  }

  protected def sendToWire(dataToDevice: ByteString): Cancellable = {
    tcpActor ! new Tcp.Write(dataToDevice, Ack)
    context.system.scheduler.scheduleOnce(config.waitingForAckTimeoutInSeconds seconds, self, AckTimeout)(context.dispatcher)
  }

  protected def terminate(reason: FSM.Reason, currentState: OutgoingDataSender.State, stateData: OutgoingDataSender.StateData) = reason match {

    case FSM.Normal => {
      log.debug(s"Stopping (normal), state $currentState, data $stateData, deviceInfo $deviceInfo.")
    }

    case FSM.Shutdown => {
      log.debug(s"Stopping (shutdown), state $currentState, data $stateData, deviceInfo $deviceInfo.")
    }

    case FSM.Failure(cause) => {
      log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData, deviceInfo $deviceInfo.")
    }
  }
}
*/ 