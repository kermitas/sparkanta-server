package as.sparkanta.ama.actor.tcp.connection

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ OneForOneStrategy, SupervisorStrategy, FSM, ActorRef, Terminated, Props, Cancellable }
import java.net.InetSocketAddress
import as.sparkanta.ama.config.AmaConfig
import akka.io.Tcp
import akka.util.{ FSMSuccessOrStop, ByteString, CompactByteString }
//import as.sparkanta.ama.actor.tcp.message.{ OutgoingMessageListener, IncomingMessageListener }
import as.sparkanta.gateway.message.{ ConnectionClosed, SoftwareVersionWasIdentified }
//import as.sparkanta.device.message.{ MessageHeader, MessageHeader65536 }

object TcpConnectionHandler {

  sealed trait State extends Serializable
  case object Unidentified extends State
  case object WaitingForData extends State

  sealed trait StateData extends Serializable
  case class UnidentifiedStateData(incomingDataBuffer: BufferedIdentificationStringWithSoftwareVersionReader, unidentifiedTimeout: Cancellable) extends StateData
  case class WaitingForDataStateData(softwareVersion: Int, incomingDataListener: ActorRef) extends StateData

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  case object IdentificationTimeout extends InternalMessage

  class ConnectionWasLostException(remoteAddress: InetSocketAddress, localAddress: InetSocketAddress, runtimeId: Long) extends Exception(s"Connection (runtimeId $runtimeId) between us ($localAddress) and remote ($remoteAddress) was lost.")
  class WatchedActorDied(diedWatchedActor: AnyRef, remoteAddress: InetSocketAddress, localAddress: InetSocketAddress, runtimeId: Long) extends Exception(s"Stopping (runtimeId $runtimeId, remoteAddress $remoteAddress, localAddress $localAddress) because watched actor $diedWatchedActor died.")
  class WatchedTcpActorDied(diedTcpWatchedActor: ActorRef, remoteAddress: InetSocketAddress, localAddress: InetSocketAddress, runtimeId: Long) extends Exception(s"Stopping (runtimeId $runtimeId, remoteAddress $remoteAddress, localAddress $localAddress) because watched tcp actor $diedTcpWatchedActor died.")
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

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Terminating because once of child actors failed.", t)))
      SupervisorStrategy.Stop
    }
  }

  {
    val unidentifiedTimeout = context.system.scheduler.scheduleOnce(config.identificationTimeoutInSeconds seconds, self, IdentificationTimeout)(context.dispatcher)
    startWith(Unidentified, new UnidentifiedStateData(new BufferedIdentificationStringWithSoftwareVersionReader(config.identificationString), unidentifiedTimeout))
  }

  when(Unidentified) {
    case Event(Tcp.Received(data), sd: UnidentifiedStateData)    => successOrStopWithFailure { analyzeIncomingData(data, sd) }

    case Event(IdentificationTimeout, sd: UnidentifiedStateData) => successOrStopWithFailure { throw new Exception(s"Identification timeout (${config.identificationTimeoutInSeconds} seconds) reached.") }
  }

  when(WaitingForData, stateTimeout = config.inactivityTimeoutInSeconds seconds) {
    case Event(Tcp.Received(data), sd: WaitingForDataStateData) => successOrStopWithFailure {
      sd.incomingDataListener ! data
      stay using sd
    }

    case Event(StateTimeout, sd: WaitingForDataStateData) => successOrStopWithFailure { throw new Exception(s"Inactivity timeout (${config.inactivityTimeoutInSeconds} seconds) reached.") }
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(Tcp.PeerClosed, stateData) => stop(FSM.Failure(new ConnectionWasLostException(remoteAddress, localAddress, runtimeId)))

    case Event(Terminated(diedWatchedActor), stateData) => {
      val exception = if (diedWatchedActor.equals(tcpActor))
        new WatchedActorDied(diedWatchedActor, remoteAddress, localAddress, runtimeId)
      else
        new WatchedTcpActorDied(tcpActor, remoteAddress, localAddress, runtimeId)

      stop(FSM.Failure(exception))
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
    context.watch(tcpActor)
  }

  protected def analyzeIncomingData(data: ByteString, sd: UnidentifiedStateData) = {
    sd.incomingDataBuffer.bufferIncomingData(data)

    sd.incomingDataBuffer.getSoftwareVersion match {
      case Some(softwareVersion) => {
        sd.unidentifiedTimeout.cancel()

        if (isSoftwareVersionCorrect(softwareVersion)) {

          val incomingDataListener: ActorRef = null // TODO start IncomingDataListener

          context.watch(incomingDataListener)
          incomingDataListener ! sd.incomingDataBuffer.getBuffer

          val outgoingDataListener: ActorRef = null // TODO start OutgoingDataListener
          context.watch(outgoingDataListener)

          // TODO publish something on broadcaster?

          amaConfig.broadcaster ! new SoftwareVersionWasIdentified(softwareVersion, remoteAddress, localAddress, runtimeId, tcpActor, self, incomingDataListener, outgoingDataListener)

          goto(WaitingForData) using new WaitingForDataStateData(softwareVersion, incomingDataListener)
        } else {
          throw new Exception(s"Software version $softwareVersion is not supported.")
        }
      }

      case None => stay using sd
    }
  }

  protected def isSoftwareVersionCorrect(softwareVersion: Int): Boolean = true // TODO: in future perform softwareVersion checking

  protected def terminate(reason: FSM.Reason, currentState: TcpConnectionHandler.State, stateData: TcpConnectionHandler.StateData): Unit = {

    val softwareVersion = stateData match {
      case WaitingForDataStateData(softwareVersion, incomingDataListener) => Some(softwareVersion)
      case _ => None
    }

    val closedByRemoteSide = reason match {
      case FSM.Failure(cause) if cause.isInstanceOf[ConnectionWasLostException] => true
      case _ => false
    }

    val tcpActorDied = reason match {
      case FSM.Failure(cause) if cause.isInstanceOf[WatchedTcpActorDied] => true
      case _ => false
    }

    val notificationToPublishOnBroadcaster = reason match {

      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData.")
        new ConnectionClosed(None, closedByRemoteSide, softwareVersion, remoteAddress, localAddress, runtimeId)
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
        new ConnectionClosed(Some(new Exception(s"${getClass.getSimpleName} actor was shut down.")), closedByRemoteSide, softwareVersion, remoteAddress, localAddress, runtimeId)
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")

        cause match {
          case t: Throwable => new ConnectionClosed(Some(t), closedByRemoteSide, softwareVersion, remoteAddress, localAddress, runtimeId)

          case unknownCause => {
            val e = new Exception(s"Failure stop with unknown cause type (${unknownCause.getClass.getSimpleName}), $unknownCause.")
            new ConnectionClosed(Some(e), closedByRemoteSide, softwareVersion, remoteAddress, localAddress, runtimeId)
          }
        }
      }
    }

    amaConfig.broadcaster ! notificationToPublishOnBroadcaster
    if (tcpActorDied) tcpActor ! Tcp.Close
  }
}