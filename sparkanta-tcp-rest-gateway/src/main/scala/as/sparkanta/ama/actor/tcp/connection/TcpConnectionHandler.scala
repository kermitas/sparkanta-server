package as.sparkanta.ama.actor.tcp.connection

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ OneForOneStrategy, SupervisorStrategy, FSM, ActorRef, Terminated, Props, Cancellable }
import java.net.InetSocketAddress
import as.sparkanta.ama.config.AmaConfig
import akka.io.Tcp
import akka.util.{ FSMSuccessOrStop, ByteString, CompactByteString }
import as.sparkanta.ama.actor.tcp.message.{ OutgoingMessageListener, IncomingMessageListener }
import as.sparkanta.gateway.message.{ ConnectionWasLost, ConnectionClosed, IncomingMessage }
import as.sparkanta.device.message.{ MessageHeader, MessageHeader65536 }

object TcpConnectionHandler {
  sealed trait State extends Serializable
  case object CompletingMessageHeader extends State
  case object CompletingMessageBody extends State

  sealed trait StateData extends Serializable
  case class CompletingMessageHeaderStateData(buffer: ByteString) extends StateData
  case class CompletingMessageBodyStateData(bodyLength: Int, buffer: ByteString) extends StateData

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  case object InactivityTimeout extends InternalMessage

  class ConnectionWasLostException(remoteAddress: InetSocketAddress, localAddress: InetSocketAddress, runtimeId: Long) extends Exception(s"Connection (runtimeId $runtimeId) between us ($localAddress) and remote ($remoteAddress) was lost.")
}

class TcpConnectionHandler(
  amaConfig:     AmaConfig,
  config:        TcpConnectionHandlerConfig,
  remoteAddress: InetSocketAddress,
  localAddress:  InetSocketAddress,
  tcpActor:      ActorRef,
  runtimeId:     Long
) extends FSM[TcpConnectionHandler.State, TcpConnectionHandler.StateData] with FSMSuccessOrStop[TcpConnectionHandler.State, TcpConnectionHandler.StateData] {

  def this(
    amaConfig:     AmaConfig,
    remoteAddress: InetSocketAddress,
    localAddress:  InetSocketAddress,
    tcpActor:      ActorRef,
    runtimeId:     Long
  ) = this(amaConfig, TcpConnectionHandlerConfig.fromTopKey(amaConfig.config), remoteAddress, localAddress, tcpActor, runtimeId)

  import TcpConnectionHandler._

  protected val messageHeader: MessageHeader = new MessageHeader65536
  protected var inactivityCancellable: Cancellable = _

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Terminating because once of child actors failed.", t)))
      SupervisorStrategy.Stop
    }
  }

  setInactivityTimeout
  startWith(CompletingMessageHeader, new CompletingMessageHeaderStateData(CompactByteString.empty))

  when(CompletingMessageHeader) {
    case Event(Tcp.Received(data), sd: CompletingMessageHeaderStateData) => successOrStopWithFailure { analyzeIncomingMessageHeader(data, sd) }
  }

  when(CompletingMessageBody) {
    case Event(Tcp.Received(data), sd: CompletingMessageBodyStateData) => successOrStopWithFailure { analyzeIncomingMessageBody(data, sd) }
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(Tcp.PeerClosed, stateData)             => connectionWasLost

    case Event(InactivityTimeout, stateData)          => inactivityTimeout

    case Event(Terminated(diedChildActor), stateData) => childActorDied(diedChildActor)

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
    startIncomingMessageListenerActor
    startOutgoingMessageListenerActor
  }

  protected def startIncomingMessageListenerActor: ActorRef = {
    val incomingMessageListenerActor = {
      val props = Props(new IncomingMessageListener(amaConfig, remoteAddress, localAddress, tcpActor, runtimeId))
      context.actorOf(props, name = classOf[IncomingMessageListener].getSimpleName + "-" + runtimeId)
    }

    context.watch(incomingMessageListenerActor)

    incomingMessageListenerActor
  }

  protected def startOutgoingMessageListenerActor: ActorRef = {
    val outgoingMessageListenerActor = {
      val props = Props(new OutgoingMessageListener(amaConfig, remoteAddress, localAddress, tcpActor, runtimeId))
      context.actorOf(props, name = classOf[OutgoingMessageListener].getSimpleName + "-" + runtimeId)
    }

    context.watch(outgoingMessageListenerActor)

    outgoingMessageListenerActor
  }

  protected def analyzeIncomingMessageHeader(data: ByteString, sd: CompletingMessageHeaderStateData) = {
    resetInactivityTimeout

    val nextState = analyzeBuffer(None, sd.buffer ++ data) match {
      case (Some(bodyLength), newBuffer) => goto(CompletingMessageBody) using new CompletingMessageBodyStateData(bodyLength, newBuffer)
      case (None, newBuffer)             => stay using new CompletingMessageHeaderStateData(newBuffer)
    }

    nextState
  }

  protected def analyzeIncomingMessageBody(data: ByteString, sd: CompletingMessageBodyStateData) = {
    resetInactivityTimeout

    val nextState = analyzeBuffer(Some(sd.bodyLength), sd.buffer ++ data) match {
      case (Some(bodyLength), newBuffer) => stay using new CompletingMessageBodyStateData(bodyLength, newBuffer)
      case (None, newBuffer)             => goto(CompletingMessageHeader) using new CompletingMessageHeaderStateData(newBuffer)
    }

    nextState
  }

  /**
   *
   * @param bodyLength if set that means that message body is read right now
   */
  protected def analyzeBuffer(bodyLength: Option[Int], buffer: ByteString): (Option[Int], ByteString) = bodyLength match {

    // body length is set, let's see if there are enough data in buffer
    case Some(bodyLength) => getAvailable(bodyLength, buffer) match {

      // we successfully took from buffer message body
      case Some((messageBody, newBuffer)) => {
        amaConfig.broadcaster ! new IncomingMessage(remoteAddress, localAddress, messageBody, tcpActor, runtimeId)
        analyzeBuffer(None, newBuffer)
      }

      // we need bodyLength bytes to be in buffer but there are less than that, let's wait for new data
      case None => {
        log.debug(s"${bodyLength - buffer.size} bytes left to read full command body")
        (Some(bodyLength), buffer)
      }
    }

    // body length is not set, let's see if there are all bytes header bytes in buffer
    case None => getAvailable(messageHeader.messageHeaderLength, buffer) match {

      // we successfully took from buffer bytes needed to calculate message body length
      case Some((messageHeaderAsBytes, newBuffer)) => {
        val bodyLength = messageHeader.readMessageLength(messageHeaderAsBytes)
        log.debug(s"Incoming command body is $bodyLength bytes.")
        analyzeBuffer(Some(bodyLength), newBuffer)
      }

      // we need messageHeader.messageHeaderLength bytes to be in buffer but there are less than that, let's wait for new data
      case None => (None, buffer)
    }
  }

  /**
   * If can will take (and remove) first numberOfBytes from buffer.
   */
  protected def getAvailable(numberOfBytes: Int, buffer: ByteString): Option[(Array[Byte], ByteString)] = if (buffer.size >= numberOfBytes) {
    val array: Array[Byte] = new Array[Byte](numberOfBytes)
    buffer.copyToArray(array, 0, numberOfBytes)
    val newBuffer = buffer.drop(numberOfBytes)
    Some((array, newBuffer))
  } else {
    None
  }

  protected def resetInactivityTimeout: Unit = {
    inactivityCancellable.cancel()
    setInactivityTimeout
  }

  protected def setInactivityTimeout: Unit = {
    import context.dispatcher
    inactivityCancellable = context.system.scheduler.schedule(config.inactivityTimeoutInSeconds seconds, config.inactivityTimeoutInSeconds seconds, self, InactivityTimeout)
  }

  protected def childActorDied(diedChildActor: ActorRef) = stop(FSM.Failure(new Exception(s"Stopping because our child actor $diedChildActor died.")))

  protected def connectionWasLost = stop(FSM.Failure(new ConnectionWasLostException(remoteAddress, localAddress, runtimeId)))

  protected def inactivityTimeout = stop(FSM.Failure(new Exception(s"Connection inactive for more than ${config.inactivityTimeoutInSeconds} seconds, closing connection.")))

  protected def terminate(reason: FSM.Reason, currentState: TcpConnectionHandler.State, stateData: TcpConnectionHandler.StateData): Unit = {
    val notificationToPublishOnBroadcaster = reason match {

      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData.")
        new ConnectionClosed(remoteAddress, localAddress, None, runtimeId)
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
        new ConnectionClosed(remoteAddress, localAddress, Some(new Exception(s"${getClass.getSimpleName} actor was shut down.")), runtimeId)
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")

        cause match {
          case cwle: ConnectionWasLostException => new ConnectionWasLost(remoteAddress, localAddress, Some(cwle), runtimeId)
          case e: Exception                     => new ConnectionClosed(remoteAddress, localAddress, Some(e), runtimeId)
          case _                                => new ConnectionClosed(remoteAddress, localAddress, None, runtimeId)
        }
      }
    }

    amaConfig.broadcaster ! notificationToPublishOnBroadcaster
    tcpActor ! Tcp.Close
  }
}
