package as.sparkanta.actor.message.incoming

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, FSM, OneForOneStrategy, SupervisorStrategy, Cancellable }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import akka.io.Tcp
import akka.util.{ FSMSuccessOrStop, ByteString }
import as.sparkanta.device.message.{ MessageFormDevice => MessageFormDeviceMarker, Ping, Disconnect, DeviceHello, ServerHello }
import as.sparkanta.device.message.length.MessageLengthHeader
import as.sparkanta.device.message.deserialize.Deserializer
import as.sparkanta.gateway.message.{ DeviceIsDown, MessageFromDevice, SparkDeviceIdWasIdentified, DataFromDevice, GetCurrentDevices, CurrentDevices }
import as.sparkanta.server.message.MessageToDevice

import scala.net.IdentifiedInetSocketAddress

object IncomingDataListener {

  lazy final val sparkDeviceIdWhenDisconnectComesBeforeSparkDeviceIdWasRead = s"[no sparkDeviceId was set before ${classOf[Disconnect].getSimpleName}]"
  lazy final val delayBeforeNextConnectionAttemptInSecondsThatWillBeSendInDisconnectToAllNonUniqueDevices = 5

  sealed trait State extends Serializable
  case object SparkDeviceIdUnidentified extends State
  case object WaitingForCurrentDevices extends State
  case object WaitingForData extends State
  case object Disconnecting extends State

  sealed trait StateData extends Serializable
  case class SparkDeviceIdUnidentifiedStateData(sparkDeviceIdIdentificationTimeout: Cancellable) extends StateData
  case class WaitingForCurrentDevicesStateData(deviceHello: DeviceHello) extends StateData
  class IdentifiedSparkDeviceId(val sparkDeviceId: String, val sparkDeviceIdIdentificationTimeInMs: Long) extends StateData
  case class WaitingForDataStateData(override val sparkDeviceId: String, override val sparkDeviceIdIdentificationTimeInMs: Long) extends IdentifiedSparkDeviceId(sparkDeviceId, sparkDeviceIdIdentificationTimeInMs)
  case class DisconnectingStateData(override val sparkDeviceId: String, override val sparkDeviceIdIdentificationTimeInMs: Long) extends IdentifiedSparkDeviceId(sparkDeviceId, sparkDeviceIdIdentificationTimeInMs)

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  case object SparkDeviceIdIdentificationTimeout extends InternalMessage
}

class IncomingDataListener(
  amaConfig:                     AmaConfig,
  config:                        IncomingDataListenerConfig,
  remoteAddress:                 IdentifiedInetSocketAddress,
  localAddress:                  IdentifiedInetSocketAddress,
  tcpActor:                      ActorRef,
  softwareVersion:               Int,
  messageLengthHeader:           MessageLengthHeader,
  messageFromDeviceDeserializer: Deserializer[MessageFormDeviceMarker]
) extends FSM[IncomingDataListener.State, IncomingDataListener.StateData] with FSMSuccessOrStop[IncomingDataListener.State, IncomingDataListener.StateData] {

  def this(
    amaConfig:                     AmaConfig,
    remoteAddress:                 IdentifiedInetSocketAddress,
    localAddress:                  IdentifiedInetSocketAddress,
    tcpActor:                      ActorRef,
    softwareVersion:               Int,
    messageLengthHeader:           MessageLengthHeader,
    messageFromDeviceDeserializer: Deserializer[MessageFormDeviceMarker]
  ) = this(amaConfig, IncomingDataListenerConfig.fromTopKey(amaConfig.config), remoteAddress, localAddress, tcpActor, softwareVersion, messageLengthHeader, messageFromDeviceDeserializer)

  import IncomingDataListener._

  protected final val bufferedMessageFromDeviceReader = new BufferedMessageFromDeviceReader(messageLengthHeader, messageFromDeviceDeserializer)

  protected final val ping = new Ping

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Terminating because once of child actors failed.", t)))
      SupervisorStrategy.Escalate
    }
  }

  {
    val sparkDeviceIdIdentificationTimeout = context.system.scheduler.scheduleOnce(config.sparkDeviceIdIdentificationTimeoutInSeconds seconds, self, SparkDeviceIdIdentificationTimeout)(context.dispatcher)
    startWith(SparkDeviceIdUnidentified, new SparkDeviceIdUnidentifiedStateData(sparkDeviceIdIdentificationTimeout))
  }

  when(SparkDeviceIdUnidentified) {
    case Event(dataFromDevice: DataFromDevice, sd: SparkDeviceIdUnidentifiedStateData)     => successOrStopWithFailure { analyzeIncomingDataFromUnidentifiedDevice(dataFromDevice.data, sd) }

    case Event(SparkDeviceIdIdentificationTimeout, sd: SparkDeviceIdUnidentifiedStateData) => successOrStopWithFailure { throw new Exception(s"Spark device id identification timeout (${config.sparkDeviceIdIdentificationTimeoutInSeconds} seconds) reached.") }

    case Event(_: Disconnect, sd: SparkDeviceIdUnidentifiedStateData)                      => goto(Disconnecting) using new DisconnectingStateData(sparkDeviceIdWhenDisconnectComesBeforeSparkDeviceIdWasRead, System.currentTimeMillis)
  }

  when(WaitingForCurrentDevices) {
    case Event(cd: CurrentDevices, sd: WaitingForCurrentDevicesStateData) => successOrStopWithFailure { checkIfSparkDeviceIdIsUnique(cd, sd) }

    case Event(_: Disconnect, sd: SparkDeviceIdUnidentifiedStateData)     => goto(Disconnecting) using new DisconnectingStateData(sparkDeviceIdWhenDisconnectComesBeforeSparkDeviceIdWasRead, System.currentTimeMillis)
  }

  when(WaitingForData, stateTimeout = config.sendPingOnIncomingDataInactivityIntervalInSeconds seconds) {
    case Event(dataFromDevice: DataFromDevice, sd: WaitingForDataStateData) => successOrStopWithFailure { analyzeIncomingDataFromIdentifiedDevice(dataFromDevice.data, sd) }

    case Event(_: Disconnect, sd: WaitingForDataStateData)                  => goto(Disconnecting) using new DisconnectingStateData(sd.sparkDeviceId, sd.sparkDeviceIdIdentificationTimeInMs)

    case Event(StateTimeout, sd: WaitingForDataStateData) => {
      log.debug(s"Nothing comes from device of remoteAddressId ${remoteAddress.id} for more than ${config.sendPingOnIncomingDataInactivityIntervalInSeconds} seconds, sending $ping.")
      amaConfig.broadcaster ! new MessageToDevice(remoteAddress.id, ping)
      stay using sd
    }
  }

  when(Disconnecting) {
    case Event(_, sd: DisconnectingStateData) => stay using sd // do not analyze anything that was read when disconnecting is in progress
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
    amaConfig.broadcaster ! new Broadcaster.Register(self, new IncomingDataListenerClassifier(remoteAddress.id))
  }

  protected def analyzeIncomingDataFromUnidentifiedDevice(dataFromDevice: ByteString, sd: SparkDeviceIdUnidentifiedStateData) = {
    log.debug(s"Received ${dataFromDevice.size} bytes from device of remoteAddressId ${remoteAddress.id}.")

    bufferedMessageFromDeviceReader.bufferIncomingData(dataFromDevice)

    bufferedMessageFromDeviceReader.getMessageFormDevice match {

      case Some(messageFromDevice: MessageFormDeviceMarker) => messageFromDevice match {

        case deviceHello: DeviceHello => {
          sd.sparkDeviceIdIdentificationTimeout.cancel

          log.debug(s"Device of remoteAddressAd ${remoteAddress.id} successfully identified (message $deviceHello) itself as spark device id '${deviceHello.sparkDeviceId}'.")

          amaConfig.broadcaster ! new GetCurrentDevices

          goto(WaitingForCurrentDevices) using new WaitingForCurrentDevicesStateData(deviceHello)
        }

        case unknownFirstMessage => throw new Exception(s"First message from device of remoteAddressId ${remoteAddress.id} should be ${classOf[DeviceHello].getSimpleName}, not $unknownFirstMessage, disconnecting.")
      }

      case None => stay using sd
    }
  }

  protected def checkIfSparkDeviceIdIsUnique(currentDevices: CurrentDevices, sd: WaitingForCurrentDevicesStateData) = {

    disconnectAllOtherDevicesOfTheSameSaprkDeviceIdIfAny(currentDevices, sd)

    amaConfig.broadcaster ! new SparkDeviceIdWasIdentified(sd.deviceHello.sparkDeviceId, softwareVersion, remoteAddress, localAddress)
    amaConfig.broadcaster ! new MessageFromDevice(sd.deviceHello.sparkDeviceId, softwareVersion, remoteAddress, localAddress, sd.deviceHello)
    amaConfig.broadcaster ! new MessageToDevice(remoteAddress.id, new ServerHello)
    self ! new DataFromDevice(ByteString.empty, softwareVersion, remoteAddress, localAddress) // empty message will make next state to execute and see if there is complete message in buffer (or there is no)
    goto(WaitingForData) using new WaitingForDataStateData(sd.deviceHello.sparkDeviceId, System.currentTimeMillis)
  }

  protected def disconnectAllOtherDevicesOfTheSameSaprkDeviceIdIfAny(currentDevices: CurrentDevices, sd: WaitingForCurrentDevicesStateData): Unit = {
    val remoteAddressIdWithTheSameSparkDeviceId = currentDevices.devices.filter(_.sparkDeviceId.isDefined).filter(_.sparkDeviceId.get.equals(sd.deviceHello.sparkDeviceId)).map(_.remoteAddress.id)
    disconnectAllOtherDevicesOfTheSameSaprkDeviceIdIfAny(remoteAddressIdWithTheSameSparkDeviceId, sd.deviceHello.sparkDeviceId)
  }

  protected def disconnectAllOtherDevicesOfTheSameSaprkDeviceIdIfAny(remoteAddressIdWithTheSameSparkDeviceId: Seq[Long], sparkDeviceId: String): Unit = if (remoteAddressIdWithTheSameSparkDeviceId.size > 0) {
    val disconnect = new Disconnect(delayBeforeNextConnectionAttemptInSecondsThatWillBeSendInDisconnectToAllNonUniqueDevices)
    log.warning(s"sparkDeviceId '${sparkDeviceId}' is already associated with ${remoteAddressIdWithTheSameSparkDeviceId.size} device(s) (remote address id(s) = ${remoteAddressIdWithTheSameSparkDeviceId.mkString(",")}) but should be unique, sending $disconnect to all of them and allow this (new) one (remote address id ${remoteAddress.id}) to continue.")
    remoteAddressIdWithTheSameSparkDeviceId.foreach(rid => amaConfig.broadcaster ! new MessageToDevice(rid, disconnect))
  }

  protected def analyzeIncomingDataFromIdentifiedDevice(dataFromDevice: ByteString, sd: WaitingForDataStateData) = {
    log.debug(s"Received ${dataFromDevice.length} bytes from device of remoteAddressId ${remoteAddress.id}, sparkDeviceId '${sd.sparkDeviceId}'.")

    bufferedMessageFromDeviceReader.bufferIncomingData(dataFromDevice)

    var messageFromDevice = bufferedMessageFromDeviceReader.getMessageFormDevice
    while (messageFromDevice.isDefined) {
      log.debug(s"Received message ${messageFromDevice.get} from device of remoteAddressId ${remoteAddress.id}, sparkDeviceId '${sd.sparkDeviceId}'.")

      amaConfig.broadcaster ! new MessageFromDevice(sd.sparkDeviceId, softwareVersion, remoteAddress, localAddress, messageFromDevice.get)
      messageFromDevice = bufferedMessageFromDeviceReader.getMessageFormDevice
    }

    stay using sd
  }

  protected def terminate(reason: FSM.Reason, currentState: IncomingDataListener.State, stateData: IncomingDataListener.StateData): Unit = {

    val sparkDeviceId = stateData match {
      case isdi: IdentifiedSparkDeviceId => Some(isdi.sparkDeviceId)
      case _                             => None
    }

    val sparkDeviceIdMessage = sparkDeviceId.map(sparkDeviceId => s", sparkDeviceId '$sparkDeviceId'.").getOrElse(".")

    reason match {

      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData, remoteAddressId ${remoteAddress.id}$sparkDeviceIdMessage")
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData, remoteAddressId ${remoteAddress.id}$sparkDeviceIdMessage")
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData, remoteAddressId ${remoteAddress.id}$sparkDeviceIdMessage")
      }
    }

    stateData match {
      case isdi: IdentifiedSparkDeviceId => amaConfig.broadcaster ! new DeviceIsDown(
        isdi.sparkDeviceId,
        softwareVersion,
        remoteAddress,
        localAddress,
        System.currentTimeMillis - isdi.sparkDeviceIdIdentificationTimeInMs
      )

      case _ =>
    }
  }
}