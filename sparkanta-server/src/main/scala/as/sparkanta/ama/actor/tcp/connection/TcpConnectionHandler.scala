package as.sparkanta.ama.actor.tcp.connection

import akka.actor.{ OneForOneStrategy, SupervisorStrategy, FSM, ActorRef, Terminated, Props }
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
  case object CompletingMessageHeaderStateData extends StateData
  case class CompletingMessageBodyStateData(bodyLength: Short) extends StateData

  sealed trait Message extends Serializable
  sealed trait OutgoingMessage extends Message
  class ConnectionWasLost(val remoteAddress: InetSocketAddress, val localAddress: InetSocketAddress) extends OutgoingMessage
  class ConnectionClosed(val remoteAddress: InetSocketAddress, val localAddress: InetSocketAddress) extends OutgoingMessage
  class IncomingMessage(val remoteAddress: InetSocketAddress, val localAddress: InetSocketAddress, val messageBody: Array[Byte], val tcpActor: ActorRef) extends OutgoingMessage

  class ConnectionWasLostException(val remoteAddress: InetSocketAddress, val localAddress: InetSocketAddress) extends Exception(s"Connection between us ($localAddress) and remote ($remoteAddress) was lost.")
}

class TcpConnectionHandler(amaConfig: AmaConfig, remoteAddress: InetSocketAddress, localAddress: InetSocketAddress) extends FSM[TcpConnectionHandler.State, TcpConnectionHandler.StateData] {

  import TcpConnectionHandler._

  protected var buffer: ByteString = CompactByteString.empty

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Escalate
  }

  startWith(CompletingMessageHeader, CompletingMessageHeaderStateData)

  when(CompletingMessageHeader) {
    case Event(Tcp.Received(data), CompletingMessageHeaderStateData) => successOrStopWithFailure { analyzeIncomingMessageHeader(data, sender()) }
  }

  when(CompletingMessageBody) {
    case Event(Tcp.Received(data), sd: CompletingMessageBodyStateData) => successOrStopWithFailure { analyzeIncomingMessageBody(data, sender(), sd) }
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(Tcp.PeerClosed, stateData)             => connectionWasLost

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

  protected def analyzeIncomingMessageHeader(data: ByteString, tcpActor: ActorRef) = {
    val newBufferAndNextState = analyzeBuffer(None, buffer ++ data, tcpActor) match {
      case (Some(bodyLength), newBuffer) => (newBuffer, goto(CompletingMessageBody) using new CompletingMessageBodyStateData(bodyLength))
      case (None, newBuffer)             => (newBuffer, stay using CompletingMessageHeaderStateData)
    }

    buffer = newBufferAndNextState._1
    newBufferAndNextState._2
  }

  protected def analyzeIncomingMessageBody(data: ByteString, tcpActor: ActorRef, sd: CompletingMessageBodyStateData) = {
    val newBufferAndNextState = analyzeBuffer(Some(sd.bodyLength), buffer ++ data, tcpActor) match {
      case (Some(bodyLength), newBuffer) => (newBuffer, stay using sd.copy(bodyLength = bodyLength))
      case (None, newBuffer)             => (newBuffer, goto(CompletingMessageHeader) using CompletingMessageHeaderStateData)
    }

    buffer = newBufferAndNextState._1
    newBufferAndNextState._2
  }

  /**
   *
   * @param bodyLength if set that means that message body is read right now
   */
  protected def analyzeBuffer(bodyLength: Option[Short], buffer: ByteString, tcpActor: ActorRef): (Option[Short], ByteString) = bodyLength match {

    // body length is set, let's see if there are enough data in buffer
    case Some(bodyLength) => getAvailable(bodyLength, buffer) match {

      // we successfully took from buffer message body
      case Some((messageBody, newBuffer)) => {
        amaConfig.broadcaster ! new IncomingMessage(remoteAddress, localAddress, messageBody, tcpActor)
        analyzeBuffer(None, newBuffer, tcpActor)
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
        analyzeBuffer(Some(bodyLength), newBuffer, tcpActor)
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

  protected def childActorDied(diedChildActor: ActorRef) = stop(FSM.Failure(new Exception(s"Stopping because our child actor $diedChildActor died.")))

  protected def connectionWasLost = stop(FSM.Failure(new ConnectionWasLostException(remoteAddress, localAddress)))

  protected def terminate(reason: FSM.Reason, currentState: TcpConnectionHandler.State, stateData: TcpConnectionHandler.StateData): Unit = {
    val notificationToPublishOnBroadcaster = reason match {

      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData")
        new ConnectionClosed(remoteAddress, localAddress)
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData")
        new ConnectionClosed(remoteAddress, localAddress)
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData")

        cause match {
          case cwle: ConnectionWasLostException => new ConnectionWasLost(cwle.remoteAddress, cwle.localAddress)
          case _                                => new ConnectionClosed(remoteAddress, localAddress)
        }
      }
    }

    amaConfig.broadcaster ! notificationToPublishOnBroadcaster
  }
}
