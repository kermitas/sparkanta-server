package as.sparkanta.ama.actor.tcp.connection

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ OneForOneStrategy, SupervisorStrategy, FSM, ActorRef, Terminated, Props, Cancellable }
import java.net.InetSocketAddress
import as.sparkanta.ama.config.AmaConfig
import akka.io.Tcp
import akka.util.{ FSMSuccessOrStop, ByteString }
import as.sparkanta.ama.actor.tcp.message.{ OutgoingDataListener, IncomingDataListener }
import as.sparkanta.gateway.message.{ DataFromDevice, ConnectionClosed, SoftwareVersionWasIdentified }

object TcpConnectionHandler {
  sealed trait State extends Serializable
  case object SoftwareVersionUnidentified extends State
  case object WaitingForData extends State

  sealed trait StateData extends Serializable
  case class SoftwareVersionUnidentifiedStateData(incomingDataReader: BufferedIdentificationStringWithSoftwareVersionReader, softwareVersionIdentificationTimeout: Cancellable) extends StateData
  case class WaitingForDataStateData(softwareVersion: Int) extends StateData

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  case object SoftwareVersionIdentificationTimeout extends InternalMessage

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
    val softwareVersionIdentificationTimeout = context.system.scheduler.scheduleOnce(config.softwareVersionIdentificationTimeoutInSeconds seconds, self, SoftwareVersionIdentificationTimeout)(context.dispatcher)
    startWith(SoftwareVersionUnidentified, new SoftwareVersionUnidentifiedStateData(new BufferedIdentificationStringWithSoftwareVersionReader(config.identificationString), softwareVersionIdentificationTimeout))
  }

  when(SoftwareVersionUnidentified) {
    case Event(Tcp.Received(data), sd: SoftwareVersionUnidentifiedStateData)                   => successOrStopWithFailure { analyzeIncomingData(data, sd) }

    case Event(SoftwareVersionIdentificationTimeout, sd: SoftwareVersionUnidentifiedStateData) => successOrStopWithFailure { throw new Exception(s"Software version identification timeout (${config.softwareVersionIdentificationTimeoutInSeconds} seconds) reached.") }
  }

  when(WaitingForData, stateTimeout = config.incomingDataInactivityTimeoutInSeconds seconds) {
    case Event(Tcp.Received(dataFromDevice), sd: WaitingForDataStateData) => successOrStopWithFailure {
      amaConfig.broadcaster ! new DataFromDevice(dataFromDevice, sd.softwareVersion, remoteAddress, localAddress, runtimeId)
      stay using sd
    }

    case Event(StateTimeout, sd: WaitingForDataStateData) => successOrStopWithFailure { throw new Exception(s"Incoming data inactivity timeout (${config.incomingDataInactivityTimeoutInSeconds} seconds) reached.") }
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

  protected def analyzeIncomingData(data: ByteString, sd: SoftwareVersionUnidentifiedStateData) = {
    sd.incomingDataReader.bufferIncomingData(data)

    sd.incomingDataReader.getSoftwareVersion match {
      case Some(softwareVersion) => {

        sd.softwareVersionIdentificationTimeout.cancel

        if (isSoftwareVersionSupported(softwareVersion)) {

          val outgoingDataListener: ActorRef = {
            val props = Props(new OutgoingDataListener(amaConfig, remoteAddress, localAddress, tcpActor, runtimeId))
            context.actorOf(props, name = classOf[OutgoingDataListener].getSimpleName + "-" + runtimeId)
          }

          context.watch(outgoingDataListener)

          // ---

          val incomingDataListener: ActorRef = {
            val props = Props(new IncomingDataListener(amaConfig, remoteAddress, localAddress, tcpActor, runtimeId, softwareVersion))
            context.actorOf(props, name = classOf[IncomingDataListener].getSimpleName + "-" + runtimeId)
          }

          context.watch(incomingDataListener)
          incomingDataListener ! new DataFromDevice(sd.incomingDataReader.getBuffer, softwareVersion, remoteAddress, localAddress, runtimeId)

          // ---

          amaConfig.broadcaster ! new SoftwareVersionWasIdentified(softwareVersion, remoteAddress, localAddress, runtimeId)

          goto(WaitingForData) using new WaitingForDataStateData(softwareVersion)
        } else {
          throw new Exception(s"Software version $softwareVersion is not supported.")
        }
      }

      case None => stay using sd
    }
  }

  protected def isSoftwareVersionSupported(softwareVersion: Int): Boolean = true // TODO: in future perform software version checking

  protected def terminate(reason: FSM.Reason, currentState: TcpConnectionHandler.State, stateData: TcpConnectionHandler.StateData): Unit = {

    val softwareVersion = stateData match {
      case WaitingForDataStateData(softwareVersion) => Some(softwareVersion)
      case _                                        => None
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