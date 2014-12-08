package as.sparkanta.ama.actor.tcp.message

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, FSM, Cancellable, OneForOneStrategy, SupervisorStrategy, Props, Terminated }
import akka.io.Tcp
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import java.net.InetSocketAddress
import scala.collection.mutable.ListBuffer
import akka.util.{ FSMSuccessOrStop, ByteString }
import as.sparkanta.gateway.message.DataToDevice
import as.sparkanta.device.message.{ MessageLengthHeaderReader, MessageToDevice => MessageToDeviceMarker }
import as.sparkanta.device.message.serialize.Serializer

object OutgoingDataListener {
  sealed trait State extends Serializable
  case object WaitingForSomethingToSend extends State
  case object WaitingForAck extends State

  sealed trait StateData extends Serializable
  case object WaitingForSomethingToSendStateData extends StateData
  case class WaitingForAckStateData(outgoingBuffer: ListBuffer[ByteString], waitingForAckTimeout: Cancellable) extends StateData

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  object Ack extends InternalMessage with Tcp.Event
  object AckTimeout extends InternalMessage
}

class OutgoingDataListener(
  amaConfig:                     AmaConfig,
  config:                        OutgoingDataListenerConfig,
  remoteAddress:                 InetSocketAddress,
  localAddress:                  InetSocketAddress,
  tcpActor:                      ActorRef,
  runtimeId:                     Long,
  val messageLengthHeaderReader: MessageLengthHeaderReader,
  val serializer:                Serializer[MessageToDeviceMarker]
) extends FSM[OutgoingDataListener.State, OutgoingDataListener.StateData] with FSMSuccessOrStop[OutgoingDataListener.State, OutgoingDataListener.StateData] {

  def this(
    amaConfig:                 AmaConfig,
    remoteAddress:             InetSocketAddress,
    localAddress:              InetSocketAddress,
    tcpActor:                  ActorRef,
    runtimeId:                 Long,
    messageLengthHeaderReader: MessageLengthHeaderReader,
    serializer:                Serializer[MessageToDeviceMarker]
  ) = this(amaConfig, OutgoingDataListenerConfig.fromTopKey(amaConfig.config), remoteAddress, localAddress, tcpActor, runtimeId, messageLengthHeaderReader, serializer)

  import OutgoingDataListener._

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Terminating because once of child actors failed.", t)))
      SupervisorStrategy.Escalate
    }
  }

  startWith(WaitingForSomethingToSend, WaitingForSomethingToSendStateData)

  when(WaitingForSomethingToSend) {
    case Event(dataToDevice: DataToDevice, WaitingForSomethingToSendStateData) => successOrStopWithFailure { sendRequestWhileNothingToDo(dataToDevice.data) }
  }

  when(WaitingForAck) {
    case Event(Ack, sd: WaitingForAckStateData)                        => successOrStopWithFailure { ackReceived(sd) }

    case Event(dataToDevice: DataToDevice, sd: WaitingForAckStateData) => successOrStopWithFailure { sendRequestWhileWaitingForAck(dataToDevice.data, sd) }

    case Event(AckTimeout, sd: WaitingForAckStateData)                 => successOrStopWithFailure { throw new Exception(s"No ACK for more than ${config.waitingForAckTimeoutInSeconds} seconds, closing connection.") }
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(Tcp.CommandFailed(_), stateData)         => { stop(FSM.Failure(new Exception("Write request failed."))) }

    case Event(Terminated(diedWatchedActor), stateData) => stop(FSM.Failure(s"Stopping (runtimeId $runtimeId, remoteAddress $remoteAddress, localAddress $localAddress) because watched actor $diedWatchedActor died."))

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
    amaConfig.broadcaster ! new Broadcaster.Register(self, new OutgoingDataListenerClassifier(runtimeId))

    val props = Props(new OutgoingMessageListener(amaConfig, runtimeId, serializer, messageLengthHeaderReader))
    val outgoingMessageListener = context.actorOf(props, name = classOf[OutgoingMessageListener].getSimpleName + "-" + runtimeId)
    context.watch(outgoingMessageListener)
  }

  protected def sendRequestWhileNothingToDo(dataToDevice: ByteString) = {
    val waitingForAckTimeout = sendToWire(dataToDevice)
    goto(WaitingForAck) using new WaitingForAckStateData(new ListBuffer[ByteString], waitingForAckTimeout)
  }

  protected def sendRequestWhileWaitingForAck(dataToDevice: ByteString, sd: WaitingForAckStateData) = if (sd.outgoingBuffer.size >= config.maximumNumberOfBufferedMessages) {
    stop(FSM.Failure(new Exception(s"Maximum number of ${config.maximumNumberOfBufferedMessages} buffered messages to send reached.")))
  } else {
    sd.outgoingBuffer += dataToDevice
    stay using sd
  }

  protected def ackReceived(sd: WaitingForAckStateData) = {

    sd.waitingForAckTimeout.cancel

    if (sd.outgoingBuffer.isEmpty) {
      goto(WaitingForSomethingToSend) using WaitingForSomethingToSendStateData
    } else {
      val dataToDevice = sd.outgoingBuffer.head
      sd.outgoingBuffer -= dataToDevice

      val waitingForAckTimeout = sendToWire(dataToDevice)

      stay using sd.copy(waitingForAckTimeout = waitingForAckTimeout)
    }
  }

  protected def sendToWire(dataToDevice: ByteString): Cancellable = {
    tcpActor ! new Tcp.Write(dataToDevice, Ack)
    context.system.scheduler.scheduleOnce(config.waitingForAckTimeoutInSeconds seconds, self, AckTimeout)(context.dispatcher)
  }

  protected def terminate(reason: FSM.Reason, currentState: OutgoingDataListener.State, stateData: OutgoingDataListener.StateData) = reason match {

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