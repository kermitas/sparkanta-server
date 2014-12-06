package as.sparkanta.ama.actor.tcp.message

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, FSM, Cancellable, OneForOneStrategy, SupervisorStrategy }
import akka.io.Tcp
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import java.net.InetSocketAddress
import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker, MessageHeader65536, MessageHeader }
import as.sparkanta.internal.message.MessageToDevice
import scala.collection.mutable.ListBuffer
import akka.util.{ FSMSuccessOrStop, ByteString }

object OutgoingMessageListener {

  sealed trait State extends Serializable
  case object WaitingForSomethingToSend extends State
  case object WaitingForAck extends State

  sealed trait StateData extends Serializable
  case object WaitingForSomethingToSendStateData extends StateData
  case object WaitingForAckStateData extends StateData

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  object Ack extends InternalMessage with Tcp.Event
  object AckTimeout extends InternalMessage
}

class OutgoingMessageListener(
  amaConfig:     AmaConfig,
  config:        OutgoingMessageListenerConfig,
  remoteAddress: InetSocketAddress,
  localAddress:  InetSocketAddress,
  tcpActor:      ActorRef,
  runtimeId:     Long
) extends FSM[OutgoingMessageListener.State, OutgoingMessageListener.StateData] with FSMSuccessOrStop[OutgoingMessageListener.State, OutgoingMessageListener.StateData] {

  def this(
    amaConfig:     AmaConfig,
    remoteAddress: InetSocketAddress,
    localAddress:  InetSocketAddress,
    tcpActor:      ActorRef,
    runtimeId:     Long
  ) = this(amaConfig, OutgoingMessageListenerConfig.fromTopKey(amaConfig.config), remoteAddress, localAddress, tcpActor, runtimeId)

  import OutgoingMessageListener._

  protected val messageHeader: MessageHeader = new MessageHeader65536
  protected val outgoingBuffer = new ListBuffer[MessageToDeviceMarker]
  protected var waitingForAckCancellable: Option[Cancellable] = None

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Terminating because once of child actors failed.", t)))
      SupervisorStrategy.Escalate
    }
  }

  startWith(WaitingForSomethingToSend, WaitingForSomethingToSendStateData)

  when(WaitingForSomethingToSend) {
    case Event(messageToDevice: MessageToDevice, WaitingForSomethingToSendStateData) => successOrStopWithFailure { sendRequestWhileNothingToDo(messageToDevice.messageToDevice) }
  }

  when(WaitingForAck) {
    case Event(Ack, WaitingForAckStateData)                              => successOrStopWithFailure { ackReceived }
    case Event(messageToDevice: MessageToDevice, WaitingForAckStateData) => successOrStopWithFailure { sendRequestWhileWaitingForAck(messageToDevice.messageToDevice) }
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(AckTimeout, stateData) => {
      stop(FSM.Failure(new Exception(s"No ACK for more than ${config.waitingForAckTimeoutInSeconds} seconds, closing connection.")))
    }

    case Event(Tcp.CommandFailed(_), stateData) => {
      stop(FSM.Failure(new Exception("Write request failed.")))
    }

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

  protected def sendRequestWhileNothingToDo(messageToDevice: MessageToDeviceMarker) = {
    serializeAndSendToWire(messageToDevice)
    setWaitingForAckTimeout
    goto(WaitingForAck) using WaitingForAckStateData
  }

  protected def sendRequestWhileWaitingForAck(messageToDevice: MessageToDeviceMarker) = if (outgoingBuffer.size >= config.maximumNumberOfBufferedMessages) {
    stop(FSM.Failure(new Exception(s"Maximum number of ${config.maximumNumberOfBufferedMessages} buffered messages to send reached.")))
  } else {
    outgoingBuffer += messageToDevice
    stay using WaitingForAckStateData
  }

  protected def serializeAndSendToWire(messageToDevice: MessageToDeviceMarker): Unit = {
    val headerWithMessageToDeviceAsByteArray = messageHeader.prepareMessageToGo(serialize(messageToDevice))
    tcpActor ! new Tcp.Write(ByteString(headerWithMessageToDeviceAsByteArray), Ack)
  }

  protected def ackReceived = if (outgoingBuffer.isEmpty) {
    cancelWaitingForAckTimeout
    goto(WaitingForSomethingToSend) using WaitingForSomethingToSendStateData
  } else {
    cancelWaitingForAckTimeout
    val messageToDevice = outgoingBuffer.head
    outgoingBuffer -= messageToDevice
    serializeAndSendToWire(messageToDevice)
    setWaitingForAckTimeout
    stay using WaitingForAckStateData
  }

  protected def serialize(messageToDevice: MessageToDeviceMarker): Array[Byte] = ??? // TODO !!

  protected def resetWaitingForAckTimeout: Unit = {
    cancelWaitingForAckTimeout
    setWaitingForAckTimeout
  }

  protected def cancelWaitingForAckTimeout: Unit = {
    waitingForAckCancellable.map(_.cancel)
    waitingForAckCancellable = None
  }

  protected def setWaitingForAckTimeout: Unit = waitingForAckCancellable match {
    case Some(waitingForAckCancellable) => throw new IllegalStateException("Can not set 'waiting for ack timeout' when it is alread set.")

    case None => {
      import context.dispatcher
      waitingForAckCancellable = Some(context.system.scheduler.schedule(config.waitingForAckTimeoutInSeconds seconds, config.waitingForAckTimeoutInSeconds seconds, self, AckTimeout))
    }
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
