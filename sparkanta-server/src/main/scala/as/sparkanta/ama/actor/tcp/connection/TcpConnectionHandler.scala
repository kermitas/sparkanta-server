package as.sparkanta.ama.actor.tcp.connection

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ OneForOneStrategy, SupervisorStrategy, FSM, ActorRef, Terminated, Props, Cancellable }
import java.net.InetSocketAddress
import as.sparkanta.ama.config.AmaConfig
import akka.io.Tcp
import akka.util.{ ByteString, CompactByteString }
import as.sparkanta.ama.actor.tcp.message.IncomingMessageListener
import java.io.{ DataInputStream, ByteArrayInputStream }

object TcpConnectionHandler {

  lazy final val messageHeaderLength: Short = 2

  sealed trait State extends Serializable
  case object CompletingMessageHeader extends State
  case object CompletingMessageBody extends State

  sealed trait StateData extends Serializable
  case class CompletingMessageHeaderStateData(buffer: ByteString) extends StateData
  case class CompletingMessageBodyStateData(bodyLength: Short, buffer: ByteString) extends StateData

  sealed trait Message extends Serializable
  sealed trait OutgoingMessage extends Message
  class ConnectionWasLost(val remoteAddress: InetSocketAddress, val localAddress: InetSocketAddress, val exception: Option[Exception]) extends OutgoingMessage
  class ConnectionClosed(val remoteAddress: InetSocketAddress, val localAddress: InetSocketAddress, val exception: Option[Exception]) extends OutgoingMessage
  class IncomingMessage(val remoteAddress: InetSocketAddress, val localAddress: InetSocketAddress, val messageBody: Array[Byte], val tcpActor: ActorRef) extends OutgoingMessage

  class ConnectionWasLostException(val remoteAddress: InetSocketAddress, val localAddress: InetSocketAddress) extends Exception(s"Connection between us ($localAddress) and remote ($remoteAddress) was lost.")
  object InactivityTimeout
}

class TcpConnectionHandler(
  amaConfig:     AmaConfig,
  config:        TcpConnectionHandlerConfig,
  remoteAddress: InetSocketAddress,
  localAddress:  InetSocketAddress,
  tcpActor:      ActorRef
) extends FSM[TcpConnectionHandler.State, TcpConnectionHandler.StateData] {

  def this(
    amaConfig:     AmaConfig,
    remoteAddress: InetSocketAddress,
    localAddress:  InetSocketAddress,
    tcpActor:      ActorRef
  ) = this(amaConfig, TcpConnectionHandlerConfig.fromTopKey(amaConfig.config), remoteAddress, localAddress, tcpActor)

  import TcpConnectionHandler._

  protected var inactivityCancellable: Cancellable = _

  override val supervisorStrategy = OneForOneStrategy() {
    case t: Throwable => {
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
  }

  protected def startIncomingMessageListenerActor: ActorRef = {
    val incomingMessageListenerActor = {
      val props = Props(new IncomingMessageListener(amaConfig, remoteAddress, localAddress))
      context.actorOf(props, name = classOf[IncomingMessageListener].getSimpleName)
    }

    context.watch(incomingMessageListenerActor)

    incomingMessageListenerActor
  }

  /**
   * Use this method to surround initialization, for example:
   *
   * when(Initializing) {
   *   case Event(true, InitializingStateData) => successOrStop { ... }
   * }
   */
  protected def successOrStopWithFailure(f: => State): State = try {
    f
  } catch {
    case e: Exception => stop(FSM.Failure(e))
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
  protected def analyzeBuffer(bodyLength: Option[Short], buffer: ByteString): (Option[Short], ByteString) = bodyLength match {

    // body length is set, let's see if there are enough data in buffer
    case Some(bodyLength) => getAvailable(bodyLength, buffer) match {

      // we successfully took from buffer message body
      case Some((messageBody, newBuffer)) => {
        amaConfig.broadcaster ! new IncomingMessage(remoteAddress, localAddress, messageBody, tcpActor)
        analyzeBuffer(None, newBuffer)
      }

      // we need bodyLength bytes to be in buffer but there are less than that, let's wait for new data
      case None => {
        log.debug(s"${bodyLength - buffer.size} bytes left to read full command body")
        (Some(bodyLength), buffer)
      }
    }

    // body length is not set, let's see if there are all bytes header bytes in buffer
    case None => getAvailable(messageHeaderLength, buffer) match {

      // we successfully took from buffer bytes needed to calculate message body length
      case Some((messageHeader, newBuffer)) => {
        val bodyLength = readShort(messageHeader)
        log.debug(s"Incoming command body is $bodyLength bytes.")
        analyzeBuffer(Some(bodyLength), newBuffer)
      }

      // we need messageHeaderLength bytes to be in buffer but there are less than that, let's wait for new data
      case None => (None, buffer)
    }
  }

  /**
   * Read two first bytes from array and calculate [[Short]].
   */
  protected def readShort(array: Array[Byte]): Short = new DataInputStream(new ByteArrayInputStream(array)).readShort

  /**
   * If can will take (and remove) first numberOfBytes from buffer.
   */
  protected def getAvailable(numberOfBytes: Short, buffer: ByteString): Option[(Array[Byte], ByteString)] = if (buffer.size >= numberOfBytes) {
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

  protected def connectionWasLost = stop(FSM.Failure(new ConnectionWasLostException(remoteAddress, localAddress)))

  protected def inactivityTimeout = stop(FSM.Failure(new Exception(s"Closing inactive connection for more than ${config.inactivityTimeoutInSeconds} seconds.")))

  protected def terminate(reason: FSM.Reason, currentState: TcpConnectionHandler.State, stateData: TcpConnectionHandler.StateData): Unit = {
    val notificationToPublishOnBroadcaster = reason match {

      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData")
        new ConnectionClosed(remoteAddress, localAddress, None)
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData")
        new ConnectionClosed(remoteAddress, localAddress, None)
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData")

        cause match {
          case cwle: ConnectionWasLostException => new ConnectionWasLost(cwle.remoteAddress, cwle.localAddress, Some(cwle))
          case e: Exception                     => new ConnectionClosed(remoteAddress, localAddress, Some(e))
          case _                                => new ConnectionClosed(remoteAddress, localAddress, None)
        }
      }
    }

    amaConfig.broadcaster ! notificationToPublishOnBroadcaster
  }
}
