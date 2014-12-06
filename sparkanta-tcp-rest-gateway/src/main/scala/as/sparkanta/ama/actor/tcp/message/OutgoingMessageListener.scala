package as.sparkanta.ama.actor.tcp.message

import akka.actor.{ ActorRef, FSM, OneForOneStrategy, SupervisorStrategy }
import akka.io.Tcp
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import java.net.InetSocketAddress
import as.sparkanta.internal.message.MessageToDevice
import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }
import scala.collection.mutable.ListBuffer
import java.io.{ DataOutputStream, ByteArrayOutputStream }
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
}

class OutgoingMessageListener(
  amaConfig:     AmaConfig,
  remoteAddress: InetSocketAddress,
  localAddress:  InetSocketAddress,
  tcpActor:      ActorRef,
  runtimeId:     Long
) extends FSM[OutgoingMessageListener.State, OutgoingMessageListener.StateData] with FSMSuccessOrStop[OutgoingMessageListener.State, OutgoingMessageListener.StateData] {

  import OutgoingMessageListener._

  protected val outgoingBuffer = new ListBuffer[MessageToDeviceMarker]

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
    goto(WaitingForAck) using WaitingForAckStateData
  }

  protected def sendRequestWhileWaitingForAck(messageToDevice: MessageToDeviceMarker) = {
    outgoingBuffer += messageToDevice
    stay using WaitingForAckStateData
  }

  protected def serializeAndSendToWire(messageToDevice: MessageToDeviceMarker): Unit = {
    val headerWithMessageToDeviceAsByteArray = addMessageHeader(serialize(messageToDevice))
    tcpActor ! new Tcp.Write(ByteString(headerWithMessageToDeviceAsByteArray), Ack)
  }

  protected def ackReceived = if (outgoingBuffer.isEmpty) {
    goto(WaitingForSomethingToSend) using WaitingForSomethingToSendStateData
  } else {
    val messageToDevice = outgoingBuffer.head
    outgoingBuffer -= messageToDevice
    serializeAndSendToWire(messageToDevice)
    stay using WaitingForAckStateData
  }

  protected def serialize(messageToDevice: MessageToDeviceMarker): Array[Byte] = ??? // TODO !!

  protected def addMessageHeader(messageToDeviceAsByteArray: Array[Byte]): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    val dos = new DataOutputStream(baos)
    dos.writeShort(messageToDeviceAsByteArray.length)
    dos.write(messageToDeviceAsByteArray)
    dos.flush
    baos.toByteArray
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
