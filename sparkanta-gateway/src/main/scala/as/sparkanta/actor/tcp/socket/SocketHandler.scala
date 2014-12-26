/*
package as.sparkanta.actor.tcp.socket

import scala.language.postfixOps
import scala.concurrent.duration._
import as.sparkanta.gateway.{ HardwareVersion, DeviceInfo }
import akka.actor.{ OneForOneStrategy, SupervisorStrategy, FSM, ActorRef, Terminated, Props, Cancellable }
import as.sparkanta.ama.config.AmaConfig
import akka.io.Tcp
import java.net.InetSocketAddress
import akka.util.{ FSMSuccessOrStop, ByteString }
//import as.sparkanta.actor.message.outgoing.OutgoingDataSender
//import as.sparkanta.actor.message.incoming.IncomingDataListener
//import as.sparkanta.gateway.message.{ SparkDeviceIdWasIdentified, DataFromDevice, ConnectionClosed, SoftwareAndHardwareVersionWasIdentified }
import as.sparkanta.gateway.message.{ DeviceIsUp, DeviceIsDown, ConnectionClosed, UnknownDeviceUniqueName }
//import as.sparkanta.device.message.length.Message255LengthHeaderCreator
//import as.sparkanta.device.message.deserialize.Deserializers
//import as.sparkanta.device.message.serialize.Serializers
import scala.net.IdentifiedInetSocketAddress
import as.sparkanta.server.message.MessageToDevice
import as.sparkanta.device.message.todevice.{ GatewayHello, ServerHello, Ping }
//import as.akka.broadcaster.Broadcaster
import scala.collection.mutable.Set

object SocketHandler {
  sealed trait State extends Serializable
  case object Identification extends State
  case object StressTest extends State
  case object WaitingForData extends State

  sealed trait StateData extends Serializable
  case class IdentificationStateData(identificationTimeout: Cancellable) extends StateData
  class IdentifiedDeviceStateData(val deviceInfo: DeviceInfo) extends StateData
  case class StressTestStateData(override val deviceInfo: DeviceInfo, val stressTestTimeout: Cancellable) extends IdentifiedDeviceStateData(deviceInfo)
  case class WaitingForDataStateData(override val deviceInfo: DeviceInfo) extends IdentifiedDeviceStateData(deviceInfo)

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  case object IdentificationTimeout extends InternalMessage
  case object IncomingDataInactivityTimeout extends InternalMessage
  case object StressTestTimeout extends InternalMessage

  class ConnectionWasLostException(remoteAddress: InetSocketAddress, localAddress: IdentifiedInetSocketAddress) extends Exception(s"Connection between remote side $remoteAddress and us $localAddress was lost.")
  class WatchedActorDied(diedWatchedActor: AnyRef, remoteAddress: InetSocketAddress, localAddress: IdentifiedInetSocketAddress) extends Exception(s"Stopping (remoteAddress $remoteAddress, localAddress $localAddress) because watched actor $diedWatchedActor died.")
  class WatchedTcpActorDied(diedTcpWatchedActor: ActorRef, remoteAddress: InetSocketAddress, localAddress: IdentifiedInetSocketAddress) extends Exception(s"Stopping (remoteAddress $remoteAddress, localAddress $localAddress) because watched TCP actor $diedTcpWatchedActor died.")
}

class SocketHandler(
  amaConfig:                AmaConfig,
  config:                   SocketHandlerConfig,
  remoteAddress:            InetSocketAddress,
  localAddress:             IdentifiedInetSocketAddress,
  deviceStaticIds:          Map[String, Long],
  staticIdsCurrentlyOnline: Set[Long],
  tcpActor:                 ActorRef
) extends FSM[SocketHandler.State, SocketHandler.StateData] with FSMSuccessOrStop[SocketHandler.State, SocketHandler.StateData] {

  def this(
    amaConfig:                AmaConfig,
    remoteAddress:            InetSocketAddress,
    localAddress:             IdentifiedInetSocketAddress,
    deviceStaticIds:          Map[String, Long],
    staticIdsCurrentlyOnline: Set[Long],
    tcpActor:                 ActorRef
  ) = this(amaConfig, SocketHandlerConfig.fromTopKey(amaConfig.config), remoteAddress, localAddress, deviceStaticIds, staticIdsCurrentlyOnline, tcpActor)

  import SocketHandler._

  protected val incomingDataBuffer = new IncomingDataBuffer(config.identificationString)
  protected var incomingDataInactivityTimeout = setIncomingDataInactivityTimeout

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Terminating because once of child actors failed.", t)))
      SupervisorStrategy.Stop
    }
  }

  {
    val identificationTimeout = context.system.scheduler.scheduleOnce(config.identificationTimeoutInSeconds seconds, self, IdentificationTimeout)(context.dispatcher)
    startWith(Identification, new IdentificationStateData(identificationTimeout))
  }

  when(Identification) {
    case Event(Tcp.Received(data), sd: IdentificationStateData)    => successOrStopWithFailure { analyzeIncomingData(data, sd) }

    case Event(IdentificationTimeout, sd: IdentificationStateData) => successOrStopWithFailure { throw new Exception(s"Device identification timeout (${config.identificationTimeoutInSeconds} seconds) exceeded.") }
  }

  when(WaitingForData, stateTimeout = config.incomingDataInactivityTimeoutInSeconds seconds) {
    case Event(Tcp.Received(dataFromDevice), sd: WaitingForDataStateData) => successOrStopWithFailure {

      // TODO implement !!!

      stay using sd
    }
  }

  /*
  when(WaitingForData, stateTimeout = config.incomingDataInactivityTimeoutInSeconds seconds) {
    case Event(Tcp.Received(dataFromDevice), sd: WaitingForDataStateData) => successOrStopWithFailure {

      val dataFromDeviceMessage = new DataFromDevice(dataFromDevice, deviceInfo)

      sd.incomingDataListener ! dataFromDeviceMessage
      amaConfig.broadcaster ! dataFromDeviceMessage
      stay using sd
    }

    case Event(StateTimeout, sd: WaitingForDataStateData) => successOrStopWithFailure { throw new Exception(s"Incoming data inactivity timeout (${config.incomingDataInactivityTimeoutInSeconds} seconds) reached.") }
  }*/

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(Tcp.PeerClosed, stateData)                => stop(FSM.Failure(new ConnectionWasLostException(remoteAddress, localAddress)))

    case Event(IncomingDataInactivityTimeout, stateData) => stop(FSM.Failure(new Exception(s"Incoming data inactivity timeout (${config.incomingDataInactivityTimeoutInSeconds} seconds) exceeded.")))

    case Event(Terminated(diedWatchedActor), stateData) => {
      val exception = if (diedWatchedActor.equals(tcpActor))
        new WatchedTcpActorDied(tcpActor, remoteAddress, localAddress)
      else
        new WatchedActorDied(diedWatchedActor, remoteAddress, localAddress)

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

  protected def resetIncomingDataInactivityTimeout: Unit = {
    incomingDataInactivityTimeout.cancel
    setIncomingDataInactivityTimeout
  }

  protected def setIncomingDataInactivityTimeout: Cancellable = {
    incomingDataInactivityTimeout = context.system.scheduler.scheduleOnce(config.incomingDataInactivityTimeoutInSeconds seconds, self, IncomingDataInactivityTimeout)(context.dispatcher)
    incomingDataInactivityTimeout
  }

  protected def analyzeIncomingData(data: ByteString, sd: IdentificationStateData) = {

    log.debug(s"Received ${data.length} bytes from $remoteAddress.")

    resetIncomingDataInactivityTimeout

    incomingDataBuffer.bufferIncomingData(data)

    incomingDataBuffer.getSoftwareAndHardwareVersionAndUniqueName match {
      case Some((softwareVersion, hwVersion, deviceUniqueName)) => {

        sd.identificationTimeout.cancel

        val hardwareVersion = HardwareVersion(hwVersion)

        log.info(s"Device $remoteAddress successfully send identification string '${config.identificationString}', software version $softwareVersion, hardware version is $hardwareVersion and device unique name '$deviceUniqueName'.")

        deviceStaticIds.get(deviceUniqueName) match {
          case Some(staticId) => staticIdsCurrentlyOnline.synchronized {
            if (staticIdsCurrentlyOnline.find(_ == staticId).isDefined) {
              throw new Exception(s"Device of static id $staticId ('$deviceUniqueName') is currently online, I can not authorize you.")
            } else {

              // according to softwareVersion we should here create all needed infrastructure for communication with device of this software version
              if (softwareVersion == 1) {

                val deviceInfo = new DeviceInfo(staticId, softwareVersion, hardwareVersion, deviceUniqueName, remoteAddress, localAddress)

                // TODO start serializer
                // TODO start data sender
                // TODO start deserializer

                amaConfig.broadcaster ! new DeviceIsUp(deviceInfo)

                staticIdsCurrentlyOnline += staticId

                config.stressTestTimeInSeconds match {
                  case Some(stressTestTimeInSeconds) => {
                    amaConfig.broadcaster ! new MessageToDevice(deviceInfo.staticId, new Ping)
                    val stressTestTimeout = context.system.scheduler.scheduleOnce(stressTestTimeInSeconds seconds, self, StressTestTimeout)(context.dispatcher)
                    goto(StressTest) using new StressTestStateData(deviceInfo, stressTestTimeout)
                  }

                  case None => {
                    amaConfig.broadcaster ! new MessageToDevice(deviceInfo.staticId, new GatewayHello)
                    amaConfig.broadcaster ! new MessageToDevice(deviceInfo.staticId, new ServerHello) // TODO in future should be send by server (not here by gateway)
                    goto(WaitingForData) using new WaitingForDataStateData(deviceInfo)
                  }
                }
              } else {
                throw new Exception(s"Software version $softwareVersion is not supported.")
              }
            }
          }

          case None => {
            amaConfig.broadcaster ! new UnknownDeviceUniqueName(deviceUniqueName, remoteAddress, localAddress)
            throw new Exception(s"Could not find static id for device unique name '$deviceUniqueName'.")
          }
        }

        /*
        if (softwareVersion == 1) {

          // according to softwareVersion we should here create all needed infrastructure for communication with device of this software version

          /*
          val softwareAndHardwareIdentifiedDeviceInfo = deviceInfo.identifySoftwareAndHardwareVersion(softwareVersion, hwVersion)
          deviceInfo = softwareAndHardwareIdentifiedDeviceInfo

          val incomingDataListener = prepareCommunicationInfrastructureForDeviceOfSoftwareVersion1(sd.incomingDataReader.getBuffer, softwareAndHardwareIdentifiedDeviceInfo)

          amaConfig.broadcaster ! new SoftwareAndHardwareVersionWasIdentified(deviceInfo)

          goto(WaitingForData) using new WaitingForDataStateData(incomingDataListener)
          */

        } else {
          throw new Exception(s"Software version $softwareVersion is not supported.")
        }
        */
      }

      case None => stay using sd
    }
  }

  /*
  protected def prepareCommunicationInfrastructureForDeviceOfSoftwareVersion1(incomingDataBuffer: ByteString, softwareAndHardwareIdentifiedDeviceInfo: SoftwareAndHardwareIdentifiedDeviceInfo): ActorRef = {

    val softwareVersion = 1

    val messageLengthHeaderCreator = new Message255LengthHeaderCreator

    // ---

    val outgoingDataSender: ActorRef = {
      val props = Props(new OutgoingDataSender(amaConfig, softwareAndHardwareIdentifiedDeviceInfo, tcpActor, messageLengthHeaderCreator, new Serializers))
      context.actorOf(props, name = classOf[OutgoingDataSender].getSimpleName + "-" + deviceInfo.remoteAddress.id)
    }

    context.watch(outgoingDataSender)

    // ---

    val incomingDataListener: ActorRef = {
      val props = Props(new IncomingDataListener(amaConfig, softwareAndHardwareIdentifiedDeviceInfo, tcpActor, messageLengthHeaderCreator, new Deserializers))
      context.actorOf(props, name = classOf[IncomingDataListener].getSimpleName + "-" + deviceInfo.remoteAddress.id)
    }

    context.watch(incomingDataListener)

    if (incomingDataBuffer.size > 0) {
      log.debug(s"Forwarding ${incomingDataBuffer.length} bytes from device of remoteAddressId ${deviceInfo.remoteAddress.id} to incoming data listener.")
      //log.debug(s"Forwarding ${incomingDataBuffer.length} bytes from device of remoteAddressId ${deviceInfo.remoteAddress.id} to incoming data listener, (${incomingDataBuffer.map("" + _).mkString(",")}).")
      incomingDataListener ! new DataFromDevice(incomingDataBuffer, deviceInfo)
    }

    // ---

    incomingDataListener
  }*/

  protected def terminate(reason: FSM.Reason, currentState: SocketHandler.State, stateData: SocketHandler.StateData): Unit = {

    val closedByRemoteSide = reason match {
      case FSM.Failure(cause) if cause.isInstanceOf[ConnectionWasLostException] => true
      case _ => false
    }

    val tcpActorDied = reason match {
      case FSM.Failure(cause) if cause.isInstanceOf[WatchedTcpActorDied] => true
      case _ => false
    }

    val deviceInfo = stateData match {
      case idsd: IdentifiedDeviceStateData => Some(idsd.deviceInfo)
      case _                               => None
    }

    val connectionClosed: ConnectionClosed = reason match {

      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData.")
        new ConnectionClosed(None, closedByRemoteSide, remoteAddress, localAddress)
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
        new ConnectionClosed(Some(new Exception(s"${getClass.getSimpleName} actor was shut down.")), closedByRemoteSide, remoteAddress, localAddress)
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")

        cause match {
          case t: Throwable => new ConnectionClosed(Some(t), closedByRemoteSide, remoteAddress, localAddress)

          case unknownCause => {
            val e = new Exception(s"Failure stop with unknown cause type of ${unknownCause.getClass.getSimpleName}, $unknownCause.")
            new ConnectionClosed(Some(e), closedByRemoteSide, remoteAddress, localAddress)
          }
        }
      }
    }

    deviceInfo.map { deviceInfo =>
      staticIdsCurrentlyOnline.synchronized { staticIdsCurrentlyOnline += deviceInfo.staticId }
      amaConfig.broadcaster ! new DeviceIsDown(deviceInfo, deviceInfo.timeInSystemInMillis)
    }

    amaConfig.broadcaster ! connectionClosed
    if (!tcpActorDied) tcpActor ! Tcp.Close
  }
}
*/ 