package as.sparkanta.actor.tcp.socket

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ OneForOneStrategy, SupervisorStrategy, FSM, ActorRef, Terminated, Props, Cancellable }
import as.sparkanta.ama.config.AmaConfig
import akka.io.Tcp
import akka.util.{ FSMSuccessOrStop, ByteString }
import as.sparkanta.actor.message.outgoing.OutgoingDataSender
import as.sparkanta.actor.message.incoming.IncomingDataListener
import as.sparkanta.gateway.message.{ DataFromDevice, ConnectionClosed, SoftwareVersionWasIdentified }
import as.sparkanta.device.message.Message65536LengthHeader
import as.sparkanta.device.message.deserialize.Deserializers
import as.sparkanta.device.message.serialize.Serializers
import scala.net.IdentifiedInetSocketAddress

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

  class ConnectionWasLostException(remoteAddress: IdentifiedInetSocketAddress, localAddress: IdentifiedInetSocketAddress) extends Exception(s"Connection between remote side $remoteAddress and us $localAddress was lost.")
  class WatchedActorDied(diedWatchedActor: AnyRef, remoteAddress: IdentifiedInetSocketAddress, localAddress: IdentifiedInetSocketAddress) extends Exception(s"Stopping (remoteAddress $remoteAddress, localAddress $localAddress) because watched actor $diedWatchedActor died.")
  class WatchedTcpActorDied(diedTcpWatchedActor: ActorRef, remoteAddress: IdentifiedInetSocketAddress, localAddress: IdentifiedInetSocketAddress) extends Exception(s"Stopping (remoteAddress $remoteAddress, localAddress $localAddress) because watched tcp actor $diedTcpWatchedActor died.")
}

class SocketHandler(
  amaConfig:     AmaConfig,
  config:        SocketHandlerConfig,
  remoteAddress: IdentifiedInetSocketAddress,
  localAddress:  IdentifiedInetSocketAddress,
  tcpActor:      ActorRef
) extends FSM[SocketHandler.State, SocketHandler.StateData] with FSMSuccessOrStop[SocketHandler.State, SocketHandler.StateData] {

  def this(
    amaConfig:     AmaConfig,
    remoteAddress: IdentifiedInetSocketAddress,
    localAddress:  IdentifiedInetSocketAddress,
    tcpActor:      ActorRef
  ) = this(amaConfig, SocketHandlerConfig.fromTopKey(amaConfig.config), remoteAddress, localAddress, tcpActor)

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
      amaConfig.broadcaster ! new DataFromDevice(dataFromDevice, sd.softwareVersion, remoteAddress, localAddress)
      stay using sd
    }

    case Event(StateTimeout, sd: WaitingForDataStateData) => successOrStopWithFailure { throw new Exception(s"Incoming data inactivity timeout (${config.incomingDataInactivityTimeoutInSeconds} seconds) reached.") }
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(Tcp.PeerClosed, stateData) => stop(FSM.Failure(new ConnectionWasLostException(remoteAddress, localAddress)))

    case Event(Terminated(diedWatchedActor), stateData) => {
      val exception = if (diedWatchedActor.equals(tcpActor))
        new WatchedActorDied(diedWatchedActor, remoteAddress, localAddress)
      else
        new WatchedTcpActorDied(tcpActor, remoteAddress, localAddress)

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

        log.debug(s"Device of runtimeId $remoteAddress successfully send identification string '${config.identificationString}' and software version $softwareVersion.")

        if (softwareVersion == 1) {

          // according to softwareVersion we should here create all needed infrastructure for communication with device of this software version

          prepareCommunicationInfrastructureForDeviceOfSoftwareVersion1(sd.incomingDataReader.getBuffer)

          amaConfig.broadcaster ! new SoftwareVersionWasIdentified(softwareVersion, remoteAddress, localAddress)

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
      val props = Props(new OutgoingDataSender(amaConfig, remoteAddress, localAddress, tcpActor, messageLengthHeader, new Serializers))
      context.actorOf(props, name = classOf[OutgoingDataSender].getSimpleName + "-" + remoteAddress.id)
    }

    context.watch(outgoingDataSender)

    // ---

    val incomingDataListener: ActorRef = {
      val props = Props(new IncomingDataListener(amaConfig, remoteAddress, localAddress, tcpActor, softwareVersion, messageLengthHeader, new Deserializers))
      context.actorOf(props, name = classOf[IncomingDataListener].getSimpleName + "-" + remoteAddress.id)
    }

    context.watch(incomingDataListener)
    incomingDataListener ! new DataFromDevice(incomingDataBuffer, softwareVersion, remoteAddress, localAddress)

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
        new ConnectionClosed(None, closedByRemoteSide, softwareVersion, remoteAddress, localAddress)
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
        new ConnectionClosed(Some(new Exception(s"${getClass.getSimpleName} actor was shut down.")), closedByRemoteSide, softwareVersion, remoteAddress, localAddress)
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")

        cause match {
          case t: Throwable => new ConnectionClosed(Some(t), closedByRemoteSide, softwareVersion, remoteAddress, localAddress)

          case unknownCause => {
            val e = new Exception(s"Failure stop with unknown cause type (${unknownCause.getClass.getSimpleName}), $unknownCause.")
            new ConnectionClosed(Some(e), closedByRemoteSide, softwareVersion, remoteAddress, localAddress)
          }
        }
      }
    }

    amaConfig.broadcaster ! connectionClosed
    if (tcpActorDied) tcpActor ! Tcp.Close
  }
}