package as.sparkanta.actor.message.incoming

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, FSM, OneForOneStrategy, SupervisorStrategy, Cancellable }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import akka.io.Tcp
import akka.util.{ FSMSuccessOrStop, ByteString }
import as.sparkanta.device.message.{ MessageFormDevice => MessageFromDeviceMarker, Ping, Pong, Disconnect, DeviceHello, GatewayHello }
import as.sparkanta.device.message.length.MessageLengthHeaderCreator
import as.sparkanta.device.message.deserialize.Deserializer
import as.sparkanta.gateway.message.{ DeviceIsDown, MessageFromDevice, SparkDeviceIdWasIdentified, DataFromDevice, GetCurrentDevices, CurrentDevices }
import as.sparkanta.server.message.MessageToDevice
import as.sparkanta.gateway.{ SoftwareAndHardwareIdentifiedDeviceInfo, SparkDeviceIdIdentifiedDeviceInfo }

object IncomingDataListener {

  lazy final val sparkDeviceIdWhenDisconnectComesBeforeSparkDeviceIdWasRead = s"[no sparkDeviceId was set before ${classOf[Disconnect].getSimpleName}]"
  lazy final val delayBeforeNextConnectionAttemptInSecondsThatWillBeSendInDisconnectToAllNonUniqueDevices = 5

  sealed trait State extends Serializable
  case object SparkDeviceIdUnidentified extends State
  case object WaitingForCurrentDevices extends State
  case object PingPongStressTest extends State
  case object WaitingForData extends State
  case object Disconnecting extends State

  sealed trait StateData extends Serializable
  case class SparkDeviceIdUnidentifiedStateData(sparkDeviceIdIdentificationTimeout: Cancellable) extends StateData
  case class WaitingForCurrentDevicesStateData(deviceHello: DeviceHello) extends StateData
  case class PingPongStressTestStateData(deviceHello: DeviceHello, pingPongCount: Long = 0) extends StateData
  class IdentifiedSparkDeviceId(val sparkDeviceIdIdentifiedDeviceInfo: SparkDeviceIdIdentifiedDeviceInfo) extends StateData
  case class WaitingForDataStateData(override val sparkDeviceIdIdentifiedDeviceInfo: SparkDeviceIdIdentifiedDeviceInfo) extends IdentifiedSparkDeviceId(sparkDeviceIdIdentifiedDeviceInfo)
  case class DisconnectingStateData(override val sparkDeviceIdIdentifiedDeviceInfo: SparkDeviceIdIdentifiedDeviceInfo) extends IdentifiedSparkDeviceId(sparkDeviceIdIdentifiedDeviceInfo)

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  case object SparkDeviceIdIdentificationTimeout extends InternalMessage
  case object StressTestTimeout extends InternalMessage
}

class IncomingDataListener(
  amaConfig:                     AmaConfig,
  config:                        IncomingDataListenerConfig,
  var deviceInfo:                SoftwareAndHardwareIdentifiedDeviceInfo,
  tcpActor:                      ActorRef,
  messageLengthHeaderCreator:    MessageLengthHeaderCreator,
  messageFromDeviceDeserializer: Deserializer[MessageFromDeviceMarker]
) extends FSM[IncomingDataListener.State, IncomingDataListener.StateData] with FSMSuccessOrStop[IncomingDataListener.State, IncomingDataListener.StateData] {

  def this(
    amaConfig:                     AmaConfig,
    deviceInfo:                    SoftwareAndHardwareIdentifiedDeviceInfo,
    tcpActor:                      ActorRef,
    messageLengthHeaderCreator:    MessageLengthHeaderCreator,
    messageFromDeviceDeserializer: Deserializer[MessageFromDeviceMarker]
  ) = this(amaConfig, IncomingDataListenerConfig.fromTopKey(amaConfig.config), deviceInfo, tcpActor, messageLengthHeaderCreator, messageFromDeviceDeserializer)

  import IncomingDataListener._

  protected final val bufferedMessageFromDeviceReader = new BufferedMessageFromDeviceReader(messageLengthHeaderCreator, messageFromDeviceDeserializer)

  protected final val ping = new Ping
  protected final val pingMessageToDevice = new MessageToDevice(deviceInfo.remoteAddress.id, ping)

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

    case Event(_: Disconnect, sd: SparkDeviceIdUnidentifiedStateData)                      => goto(Disconnecting) using new DisconnectingStateData(deviceInfo.identifySparkDeviceId(sparkDeviceIdWhenDisconnectComesBeforeSparkDeviceIdWasRead, None))
  }

  when(WaitingForCurrentDevices) {
    case Event(dataFromDevice: DataFromDevice, sd: WaitingForCurrentDevicesStateData) => successOrStopWithFailure {
      bufferedMessageFromDeviceReader.bufferIncomingData(dataFromDevice.data)
      stay using sd
    }

    case Event(cd: CurrentDevices, sd: WaitingForCurrentDevicesStateData) => successOrStopWithFailure { checkIfSparkDeviceIdIsUnique(cd, sd) }

    case Event(_: Disconnect, sd: SparkDeviceIdUnidentifiedStateData)     => goto(Disconnecting) using new DisconnectingStateData(deviceInfo.identifySparkDeviceId(sparkDeviceIdWhenDisconnectComesBeforeSparkDeviceIdWasRead, None))
  }

  when(PingPongStressTest) {
    case Event(dataFromDevice: DataFromDevice, sd: PingPongStressTestStateData) => successOrStopWithFailure { analyzeIncomingDataDuringPingPongStressTest(dataFromDevice, sd) }

    case Event(StressTestTimeout, sd: PingPongStressTestStateData)              => successOrStopWithFailure { stopPingPongStressTest(sd) }

    case Event(_: Disconnect, sd: PingPongStressTestStateData)                  => goto(Disconnecting) using new DisconnectingStateData(deviceInfo.identifySparkDeviceId(sd.deviceHello.sparkDeviceId, None))
  }

  when(WaitingForData, stateTimeout = config.sendPingOnIncomingDataInactivityIntervalInSeconds seconds) {
    case Event(dataFromDevice: DataFromDevice, sd: WaitingForDataStateData) => successOrStopWithFailure { analyzeIncomingDataFromIdentifiedDevice(dataFromDevice.data, sd) }

    case Event(_: Disconnect, sd: WaitingForDataStateData)                  => goto(Disconnecting) using new DisconnectingStateData(sd.sparkDeviceIdIdentifiedDeviceInfo)

    case Event(StateTimeout, sd: WaitingForDataStateData) => {
      log.debug(s"Nothing comes from device of remoteAddressId ${deviceInfo.remoteAddress.id} for more than ${config.sendPingOnIncomingDataInactivityIntervalInSeconds} seconds, sending $ping.")
      amaConfig.broadcaster ! pingMessageToDevice
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
    amaConfig.broadcaster ! new Broadcaster.Register(self, new IncomingDataListenerClassifier(deviceInfo.remoteAddress.id))
  }

  protected def analyzeIncomingDataFromUnidentifiedDevice(dataFromDevice: ByteString, sd: SparkDeviceIdUnidentifiedStateData) = {
    //log.debug(s"Received ${dataFromDevice.size} bytes from device of remoteAddressId ${deviceInfo.remoteAddress.id}.")

    bufferedMessageFromDeviceReader.bufferIncomingData(dataFromDevice)

    log.debug(s"Received ${dataFromDevice.length} bytes from device of remoteAddressId ${deviceInfo.remoteAddress.id}, currently buffered ${bufferedMessageFromDeviceReader.buffer.size} (${bufferedMessageFromDeviceReader.buffer.map("" + _).mkString(",")}).")

    bufferedMessageFromDeviceReader.getMessageFormDevice match {

      case Some(messageFromDevice: MessageFromDeviceMarker) => messageFromDevice match {

        case deviceHello: DeviceHello => {
          sd.sparkDeviceIdIdentificationTimeout.cancel

          log.debug(s"Device of remoteAddressAd ${deviceInfo.remoteAddress.id} identified (message $deviceHello) itself as spark device id '${deviceHello.sparkDeviceId}', checking if it is unique in system.")

          amaConfig.broadcaster ! new GetCurrentDevices

          goto(WaitingForCurrentDevices) using new WaitingForCurrentDevicesStateData(deviceHello)
        }

        case unknownFirstMessage => throw new Exception(s"First message from device of remoteAddressId ${deviceInfo.remoteAddress.id} should be ${classOf[DeviceHello].getSimpleName}, not $unknownFirstMessage, disconnecting.")
      }

      case None => stay using sd
    }
  }

  protected def checkIfSparkDeviceIdIsUnique(currentDevices: CurrentDevices, sd: WaitingForCurrentDevicesStateData) = {
    disconnectAllOtherDevicesOfTheSameSaprkDeviceIdIfAny(currentDevices, sd)
    startPingPongStressTest(sd)
  }

  protected def startPingPongStressTest(sd: WaitingForCurrentDevicesStateData) = config.stressTestTimeoutInSeconds match {

    case Some(stressTestTimeoutInSeconds) => {
      amaConfig.broadcaster ! pingMessageToDevice
      context.system.scheduler.scheduleOnce(config.stressTestTimeoutInSeconds.get seconds, self, StressTestTimeout)(context.dispatcher)

      goto(PingPongStressTest) using new PingPongStressTestStateData(sd.deviceHello)
    }

    case None => gotoWaitingForData(sd.deviceHello, None)
  }

  protected def analyzeIncomingDataDuringPingPongStressTest(dataFromDevice: DataFromDevice, sd: PingPongStressTestStateData) = {
    val messagesFromDevice = deserializeMessages(dataFromDevice.data, sd.deviceHello.sparkDeviceId)

    if (messagesFromDevice.size == 0) {
      stay using sd
    } else if (messagesFromDevice.size == 1) {
      receivedMessageFromDeviceDuringStressTest(messagesFromDevice(0), sd)
    } else {
      throw new Exception(s"Device should not send more than one message in response on ${classOf[Ping].getSimpleName} during ping-pong stress test (but it send ${messagesFromDevice.size} messages: ${messagesFromDevice.mkString(",")}).")
    }
  }

  protected def receivedMessageFromDeviceDuringStressTest(messageFromDevice: MessageFromDeviceMarker, sd: PingPongStressTestStateData) = if (messageFromDevice.isInstanceOf[Pong]) {
    amaConfig.broadcaster ! pingMessageToDevice
    stay using sd.copy(pingPongCount = sd.pingPongCount + 1)
  } else {
    throw new Exception(s"During ping-pong stress test only ${classOf[Pong].getSimpleName} messages can come from device (but received ${messageFromDevice.getClass.getSimpleName}.")
  }

  protected def stopPingPongStressTest(sd: PingPongStressTestStateData) = {
    val pingPongCountPerSecond = sd.pingPongCount / config.stressTestTimeoutInSeconds.get
    gotoWaitingForData(sd.deviceHello, Some(pingPongCountPerSecond))
  }

  protected def gotoWaitingForData(deviceHello: DeviceHello, pingPongCountPerSecond: Option[Long]) = {
    val sparkDeviceIdIdentifiedDeviceInfo = deviceInfo.identifySparkDeviceId(deviceHello.sparkDeviceId, pingPongCountPerSecond)
    deviceInfo = sparkDeviceIdIdentifiedDeviceInfo

    log.info("===================== pingPongCountPerSecond=" + pingPongCountPerSecond)

    amaConfig.broadcaster ! new SparkDeviceIdWasIdentified(sparkDeviceIdIdentifiedDeviceInfo, pingPongCountPerSecond)
    amaConfig.broadcaster ! new MessageFromDevice(sparkDeviceIdIdentifiedDeviceInfo, deviceHello)
    amaConfig.broadcaster ! new MessageToDevice(sparkDeviceIdIdentifiedDeviceInfo.remoteAddress.id, new GatewayHello)

    //self ! new DataFromDevice(ByteString.empty, deviceInfo) // empty message will make next state to execute and see if there is complete message in buffer (or there is no)

    goto(WaitingForData) using new WaitingForDataStateData(sparkDeviceIdIdentifiedDeviceInfo)
  }

  protected def disconnectAllOtherDevicesOfTheSameSaprkDeviceIdIfAny(currentDevices: CurrentDevices, sd: WaitingForCurrentDevicesStateData): Unit = {
    val remoteAddressIdWithTheSameSparkDeviceId = currentDevices.devices.filter(_.isInstanceOf[SparkDeviceIdIdentifiedDeviceInfo]).map(_.asInstanceOf[SparkDeviceIdIdentifiedDeviceInfo]).filter(_.sparkDeviceId.equals(sd.deviceHello.sparkDeviceId)).map(_.remoteAddress.id)
    disconnectAllOtherDevicesOfTheSameSaprkDeviceIdIfAny(remoteAddressIdWithTheSameSparkDeviceId, sd.deviceHello.sparkDeviceId)
  }

  protected def disconnectAllOtherDevicesOfTheSameSaprkDeviceIdIfAny(remoteAddressIdWithTheSameSparkDeviceId: Seq[Long], sparkDeviceId: String): Unit = if (remoteAddressIdWithTheSameSparkDeviceId.size > 0) {
    val disconnect = new Disconnect(delayBeforeNextConnectionAttemptInSecondsThatWillBeSendInDisconnectToAllNonUniqueDevices)
    log.warning(s"sparkDeviceId '${sparkDeviceId}' is already associated with ${remoteAddressIdWithTheSameSparkDeviceId.size} device(s) (remote address id(s) = ${remoteAddressIdWithTheSameSparkDeviceId.mkString(",")}) but should be unique, sending $disconnect to all of them and allow this (new) one (remote address id ${deviceInfo.remoteAddress.id}) to continue.")
    remoteAddressIdWithTheSameSparkDeviceId.foreach(rid => amaConfig.broadcaster ! new MessageToDevice(rid, disconnect))
  }

  protected def analyzeIncomingDataFromIdentifiedDevice(dataFromDevice: ByteString, sd: WaitingForDataStateData) = {
    val messagesFromDevice = deserializeMessages(dataFromDevice, sd.sparkDeviceIdIdentifiedDeviceInfo.sparkDeviceId)
    messagesFromDevice.foreach(amaConfig.broadcaster ! new MessageFromDevice(sd.sparkDeviceIdIdentifiedDeviceInfo, _))
    stay using sd
  }

  protected def deserializeMessages(dataFromDevice: ByteString, sparkDeviceId: String): Seq[MessageFromDeviceMarker] = {

    bufferedMessageFromDeviceReader.bufferIncomingData(dataFromDevice)

    log.debug(s"Received ${dataFromDevice.length} bytes from device of remoteAddressId ${deviceInfo.remoteAddress.id}, sparkDeviceId '$sparkDeviceId', currently buffered ${bufferedMessageFromDeviceReader.buffer.size} (${bufferedMessageFromDeviceReader.buffer.map("" + _).mkString(",")}).")

    val result = new ListBuffer[MessageFromDeviceMarker]

    var messageFromDevice = bufferedMessageFromDeviceReader.getMessageFormDevice
    while (messageFromDevice.isDefined) {
      log.debug(s"Received message ${messageFromDevice.get} from device of remoteAddressId ${deviceInfo.remoteAddress.id}, sparkDeviceId '$sparkDeviceId'.")

      result += messageFromDevice.get
      messageFromDevice = bufferedMessageFromDeviceReader.getMessageFormDevice
    }

    result
  }

  protected def terminate(reason: FSM.Reason, currentState: IncomingDataListener.State, stateData: IncomingDataListener.StateData): Unit = {

    val sparkDeviceId = stateData match {
      case isdi: IdentifiedSparkDeviceId => Some(isdi.sparkDeviceIdIdentifiedDeviceInfo.sparkDeviceId)
      case _                             => None
    }

    val sparkDeviceIdMessage = sparkDeviceId.map(sparkDeviceId => s", sparkDeviceId '$sparkDeviceId'.").getOrElse(".")

    reason match {

      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData, remoteAddressId ${deviceInfo.remoteAddress.id}$sparkDeviceIdMessage")
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData, remoteAddressId ${deviceInfo.remoteAddress.id}$sparkDeviceIdMessage")
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData, remoteAddressId ${deviceInfo.remoteAddress.id}$sparkDeviceIdMessage")
      }
    }

    stateData match {
      case isdi: IdentifiedSparkDeviceId => amaConfig.broadcaster ! new DeviceIsDown(
        isdi.sparkDeviceIdIdentifiedDeviceInfo,
        isdi.sparkDeviceIdIdentifiedDeviceInfo.timeInSystem
      )

      case _ =>
    }
  }
}