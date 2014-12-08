package as.sparkanta.ama.actor.tcp.message

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, FSM, OneForOneStrategy, SupervisorStrategy, Cancellable }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import java.net.InetSocketAddress
import akka.io.Tcp
import Tcp._
import akka.util.{ FSMSuccessOrStop, ByteString }
import as.sparkanta.device.message.{ MessageFormDevice => MessageFormDeviceMarker, MessageLengthHeader, Hello }
import as.sparkanta.device.message.deserialize.Deserializer
import as.sparkanta.gateway.message.{ DeviceIsDown, MessageFromDevice, SparkDeviceIdWasIdentified, DataFromDevice }

object IncomingDataListener {
  sealed trait State extends Serializable
  case object SparkDeviceIdUnidentified extends State
  case object WaitingForData extends State

  sealed trait StateData extends Serializable
  case class SparkDeviceIdUnidentifiedStateData(sparkDeviceIdIdentificationTimeout: Cancellable) extends StateData
  case class WaitingForDataStateData(sparkDeviceId: String, sparkDeviceIdIdentificationTimeInMs: Long) extends StateData

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  case object SparkDeviceIdIdentificationTimeout extends InternalMessage
}

class IncomingDataListener(
  amaConfig:                     AmaConfig,
  config:                        IncomingDataListenerConfig,
  remoteAddress:                 InetSocketAddress,
  localAddress:                  InetSocketAddress,
  tcpActor:                      ActorRef,
  runtimeId:                     Long,
  softwareVersion:               Int,
  messageLengthHeader:           MessageLengthHeader,
  messageFromDeviceDeserializer: Deserializer[MessageFormDeviceMarker]
) extends FSM[IncomingDataListener.State, IncomingDataListener.StateData] with FSMSuccessOrStop[IncomingDataListener.State, IncomingDataListener.StateData] {

  def this(
    amaConfig:                     AmaConfig,
    remoteAddress:                 InetSocketAddress,
    localAddress:                  InetSocketAddress,
    tcpActor:                      ActorRef,
    runtimeId:                     Long,
    softwareVersion:               Int,
    messageLengthHeader:           MessageLengthHeader,
    messageFromDeviceDeserializer: Deserializer[MessageFormDeviceMarker]
  ) = this(amaConfig, IncomingDataListenerConfig.fromTopKey(amaConfig.config), remoteAddress, localAddress, tcpActor, runtimeId, softwareVersion, messageLengthHeader, messageFromDeviceDeserializer)

  import IncomingDataListener._

  protected val bufferedMessageFromDeviceReader = new BufferedMessageFromDeviceReader(messageLengthHeader, messageFromDeviceDeserializer)

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
  }

  when(WaitingForData) {
    case Event(dataFromDevice: DataFromDevice, sd: WaitingForDataStateData) => successOrStopWithFailure { analyzeIncomingDataFromIdentifiedDevice(dataFromDevice.data, sd) }
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
    amaConfig.broadcaster ! new Broadcaster.Register(self, new IncomingDataListenerClassifier(runtimeId))
  }

  protected def analyzeIncomingDataFromUnidentifiedDevice(dataFromDevice: ByteString, sd: SparkDeviceIdUnidentifiedStateData) = {
    log.debug(s"Received ${dataFromDevice.size} bytes from device of runtimeId $runtimeId.")

    bufferedMessageFromDeviceReader.bufferIncomingData(dataFromDevice)

    bufferedMessageFromDeviceReader.getMessageFormDevice match {

      case Some(messageFromDevice: MessageFormDeviceMarker) => messageFromDevice match {

        case hello: Hello => {
          sd.sparkDeviceIdIdentificationTimeout.cancel

          log.debug(s"Device runtimeId $runtimeId successfully identified (${hello.getClass.getSimpleName} message) itself as spark device id '${hello.sparkDeviceId}'.")

          amaConfig.broadcaster ! new SparkDeviceIdWasIdentified(hello.sparkDeviceId, softwareVersion, remoteAddress, localAddress, runtimeId)
          amaConfig.broadcaster ! new MessageFromDevice(runtimeId, hello)
          self ! new DataFromDevice(ByteString.empty, softwareVersion, remoteAddress, localAddress, runtimeId) // empty message will make next state to execute and see if there is complete message in buffer (or there is no)
          goto(WaitingForData) using new WaitingForDataStateData(hello.sparkDeviceId, System.currentTimeMillis)
        }

        case unknownFirstMessage => throw new Exception(s"First message from device of runtimeId $runtimeId should be ${classOf[Hello].getSimpleName}, not ${unknownFirstMessage.getClass.getSimpleName}.")
      }

      case None => stay using sd
    }
  }

  protected def analyzeIncomingDataFromIdentifiedDevice(dataFromDevice: ByteString, sd: WaitingForDataStateData) = {
    log.debug(s"Received ${dataFromDevice.length} bytes from device of runtimeId $runtimeId, sparkDeviceId '${sd.sparkDeviceId}'.")

    bufferedMessageFromDeviceReader.bufferIncomingData(dataFromDevice)

    var messageFromDevice = bufferedMessageFromDeviceReader.getMessageFormDevice
    while (messageFromDevice.isDefined) {
      log.debug(s"Received message ${messageFromDevice.get.getClass.getSimpleName} from device of runtimeId $runtimeId, sparkDeviceId '${sd.sparkDeviceId}'.")

      amaConfig.broadcaster ! new MessageFromDevice(runtimeId, messageFromDevice.get)
      messageFromDevice = bufferedMessageFromDeviceReader.getMessageFormDevice
    }

    stay using sd
  }

  protected def terminate(reason: FSM.Reason, currentState: IncomingDataListener.State, stateData: IncomingDataListener.StateData): Unit = {

    val sparkDeviceId = stateData match {
      case WaitingForDataStateData(sparkDeviceId, identificationTimeInMs) => Some(sparkDeviceId)
      case _ => None
    }

    stateData match {
      case WaitingForDataStateData(sparkDeviceId, identificationTimeInMs) => amaConfig.broadcaster ! new DeviceIsDown(runtimeId, sparkDeviceId, System.currentTimeMillis - identificationTimeInMs)
      case _ =>
    }

    val sparkDeviceIdMessage = sparkDeviceId.map(sparkDeviceId => s", sparkDeviceId '$sparkDeviceId'.").getOrElse(".")

    reason match {

      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData, runtimeId $runtimeId$sparkDeviceIdMessage")
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData, runtimeId $runtimeId$sparkDeviceIdMessage")
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData, runtimeId $runtimeId$sparkDeviceIdMessage")
      }
    }
  }
}