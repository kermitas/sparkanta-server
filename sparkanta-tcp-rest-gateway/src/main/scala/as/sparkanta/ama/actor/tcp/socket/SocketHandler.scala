package as.sparkanta.ama.actor.tcp.socket

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ OneForOneStrategy, SupervisorStrategy, FSM, ActorRef, Terminated, Props, Cancellable }
import as.sparkanta.ama.config.AmaConfig
import akka.io.Tcp
import akka.util.{ FSMSuccessOrStop, ByteString }
import as.sparkanta.ama.actor.message.outgoing.OutgoingDataSender
import as.sparkanta.ama.actor.message.incoming.IncomingDataListener
import as.sparkanta.gateway.message.{ DataFromDevice, ConnectionClosed, SoftwareVersionWasIdentified }
import as.sparkanta.device.message.Message65536LengthHeader
import as.sparkanta.device.message.deserialize.Deserializers
import as.sparkanta.device.message.serialize.Serializers

object SocketHandler {
  sealed trait State extends Serializable
  case object SoftwareVersionUnidentified extends State
  case object WaitingForData extends State

  sealed trait StateData extends Serializable
  case class SoftwareVersionUnidentifiedStateData(incomingDataReader: BufferedIdentificationStringWithSoftwareVersionReader, softwareVersionIdentificationTimeout: Cancellable) extends StateData
  case class WaitingForDataStateData(softwareVersion: Int) extends StateData

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  case object SoftwareVersionIdentificationTimeout extends InternalMessage

  class ConnectionWasLostException(remoteIp: String, remotePort: Int, localIp: String, localPort: Int, runtimeId: Long) extends Exception(s"Connection (runtimeId $runtimeId) between us $localIp:$localPort and remote $remoteIp:$remotePort was lost.")
  class WatchedActorDied(diedWatchedActor: AnyRef, remoteIp: String, remotePort: Int, localIp: String, localPort: Int, runtimeId: Long) extends Exception(s"Stopping (runtimeId $runtimeId, remoteAddress $remoteIp:$remotePort, localAddress $localIp:$localPort) because watched actor $diedWatchedActor died.")
  class WatchedTcpActorDied(diedTcpWatchedActor: ActorRef, remoteIp: String, remotePort: Int, localIp: String, localPort: Int, runtimeId: Long) extends Exception(s"Stopping (runtimeId $runtimeId, remoteAddress $remoteIp:$remotePort, localAddress $localIp:$localPort) because watched tcp actor $diedTcpWatchedActor died.")
}

class SocketHandler(
  amaConfig:  AmaConfig,
  config:     SocketHandlerConfig,
  remoteIp:   String,
  remotePort: Int,
  localIp:    String,
  localPort:  Int,
  tcpActor:   ActorRef,
  runtimeId:  Long
) extends FSM[SocketHandler.State, SocketHandler.StateData] with FSMSuccessOrStop[SocketHandler.State, SocketHandler.StateData] {

  def this(
    amaConfig:  AmaConfig,
    remoteIp:   String,
    remotePort: Int,
    localIp:    String,
    localPort:  Int,
    tcpActor:   ActorRef,
    runtimeId:  Long
  ) = this(amaConfig, SocketHandlerConfig.fromTopKey(amaConfig.config), remoteIp, remotePort, localIp, localPort, tcpActor, runtimeId)

  import SocketHandler._

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
      amaConfig.broadcaster ! new DataFromDevice(dataFromDevice, sd.softwareVersion, remoteIp, remotePort, localIp, localPort, runtimeId)
      stay using sd
    }

    case Event(StateTimeout, sd: WaitingForDataStateData) => successOrStopWithFailure { throw new Exception(s"Incoming data inactivity timeout (${config.incomingDataInactivityTimeoutInSeconds} seconds) reached.") }
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(Tcp.PeerClosed, stateData) => stop(FSM.Failure(new ConnectionWasLostException(remoteIp, remotePort, localIp, localPort, runtimeId)))

    case Event(Terminated(diedWatchedActor), stateData) => {
      val exception = if (diedWatchedActor.equals(tcpActor))
        new WatchedActorDied(diedWatchedActor, remoteIp, remotePort, localIp, localPort, runtimeId)
      else
        new WatchedTcpActorDied(tcpActor, remoteIp, remotePort, localIp, localPort, runtimeId)

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

        log.debug(s"Device of runtimeId $runtimeId successfully send identification string ('${config.identificationString}') and software version $softwareVersion.")

        if (softwareVersion == 1) {

          // according to softwareVersion we should here create all needed infrastructure for communication with device of this software version

          prepareCommunicationInfrastructureForDeviceOfSoftwareVersion1(sd.incomingDataReader.getBuffer)

          amaConfig.broadcaster ! new SoftwareVersionWasIdentified(softwareVersion, remoteIp, remotePort, localIp, localPort, runtimeId)

          goto(WaitingForData) using new WaitingForDataStateData(softwareVersion)

        } else {
          throw new Exception(s"Software version $softwareVersion is not supported.")
        }
      }

      case None => stay using sd
    }
  }

  protected def prepareCommunicationInfrastructureForDeviceOfSoftwareVersion1(incomingDataBuffer: ByteString): Unit = {

    val softwareVersion = 1

    val messageLengthHeader = new Message65536LengthHeader

    // ---

    val outgoingDataSender: ActorRef = {
      val props = Props(new OutgoingDataSender(amaConfig, remoteIp, remotePort, localIp, localPort, tcpActor, runtimeId, messageLengthHeader, new Serializers))
      context.actorOf(props, name = classOf[OutgoingDataSender].getSimpleName + "-" + runtimeId)
    }

    context.watch(outgoingDataSender)

    // ---

    val incomingDataListener: ActorRef = {
      val props = Props(new IncomingDataListener(amaConfig, remoteIp, remotePort, localIp, localPort, tcpActor, runtimeId, softwareVersion, messageLengthHeader, new Deserializers))
      context.actorOf(props, name = classOf[IncomingDataListener].getSimpleName + "-" + runtimeId)
    }

    context.watch(incomingDataListener)
    incomingDataListener ! new DataFromDevice(incomingDataBuffer, softwareVersion, remoteIp, remotePort, localIp, localPort, runtimeId)

    // ---
  }

  protected def terminate(reason: FSM.Reason, currentState: SocketHandler.State, stateData: SocketHandler.StateData): Unit = {

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

    val connectionClosed: ConnectionClosed = reason match {

      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData.")
        new ConnectionClosed(None, closedByRemoteSide, softwareVersion, remoteIp, remotePort, localIp, localPort, runtimeId)
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
        new ConnectionClosed(Some(new Exception(s"${getClass.getSimpleName} actor was shut down.")), closedByRemoteSide, softwareVersion, remoteIp, remotePort, localIp, localPort, runtimeId)
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")

        cause match {
          case t: Throwable => new ConnectionClosed(Some(t), closedByRemoteSide, softwareVersion, remoteIp, remotePort, localIp, localPort, runtimeId)

          case unknownCause => {
            val e = new Exception(s"Failure stop with unknown cause type (${unknownCause.getClass.getSimpleName}), $unknownCause.")
            new ConnectionClosed(Some(e), closedByRemoteSide, softwareVersion, remoteIp, remotePort, localIp, localPort, runtimeId)
          }
        }
      }
    }

    amaConfig.broadcaster ! connectionClosed
    if (tcpActorDied) tcpActor ! Tcp.Close
  }
}