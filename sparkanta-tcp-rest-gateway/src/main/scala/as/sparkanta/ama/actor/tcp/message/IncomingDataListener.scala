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
import as.sparkanta.device.message.{ MessageFormDevice => MessageFormDeviceMarker, Hello }
import as.sparkanta.device.message.deserialize.{ Deserializers, Deserializer }
//import as.sparkanta.gateway.message.IncomingMessage
//import as.sparkanta.internal.message.{ DeviceIsDown, MessageFromDevice }
import as.sparkanta.internal.message.DeviceIsDown

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
  amaConfig:     AmaConfig,
  config:        IncomingDataListenerConfig,
  remoteAddress: InetSocketAddress,
  localAddress:  InetSocketAddress,
  tcpActor:      ActorRef,
  runtimeId:     Long
) extends FSM[IncomingDataListener.State, IncomingDataListener.StateData] with FSMSuccessOrStop[IncomingDataListener.State, IncomingDataListener.StateData] {

  def this(
    amaConfig:     AmaConfig,
    remoteAddress: InetSocketAddress,
    localAddress:  InetSocketAddress,
    tcpActor:      ActorRef,
    runtimeId:     Long
  ) = this(amaConfig, IncomingDataListenerConfig.fromTopKey(amaConfig.config), remoteAddress, localAddress, tcpActor, runtimeId)

  import IncomingDataListener._

  protected val deserializers: Deserializer[MessageFormDeviceMarker] = new Deserializers

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
    case Event(dataFromDevice: ByteString, sd: SparkDeviceIdUnidentifiedStateData)         => successOrStopWithFailure { analyzeIncomingMessageFromUnidentifiedDevice(dataFromDevice, sd) }

    case Event(SparkDeviceIdIdentificationTimeout, sd: SparkDeviceIdUnidentifiedStateData) => successOrStopWithFailure { throw new Exception(s"Spark device id identification timeout (${config.sparkDeviceIdIdentificationTimeoutInSeconds} seconds) reached.") }
  }

  when(WaitingForData) {
    case Event(dataFromDevice: ByteString, sd: WaitingForDataStateData) => successOrStopWithFailure { analyzeIncomingMessageFromIdentifiedDevice(dataFromDevice, sd) }
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

  protected def analyzeIncomingMessageFromUnidentifiedDevice(dataFromDevice: ByteString, sd: SparkDeviceIdUnidentifiedStateData) = {
    log.debug(s"Received ${dataFromDevice.size} bytes from runtimeId $runtimeId.")

    // TODO: if Hello then cancel timeout and go to next state

    stay using sd
  }

  protected def analyzeIncomingMessageFromIdentifiedDevice(dataFromDevice: ByteString, sd: WaitingForDataStateData) = {
    log.debug(s"Received ${dataFromDevice.length} bytes from runtimeId $runtimeId, sparkDeviceId ${sd.sparkDeviceId}.")

    stay using sd
  }

  /*
  protected def analyzeIncomingMessageFromUnidentifiedDevice(dataFromDevice: ByteString) = {
    log.debug(s"Received ${dataFromDevice.size} bytes from unidentified device.")

    deserialize(incomingMessage.messageBody) match {
      case hello: Hello => {
        log.debug(s"Device of runtimeId $runtimeId identified itself as sparkDeviceId '${hello.sparkDeviceId}', softwareVersion ${hello.softwareVersion}.")

        if (isSoftwareVersionSupported(hello.softwareVersion)) {
          amaConfig.broadcaster ! new MessageFromDevice(runtimeId, hello)
          goto(Identified) using new IdentifiedStateData(hello.sparkDeviceId, hello.softwareVersion, System.currentTimeMillis)
        } else {
          stop(FSM.Failure(new Exception(s"Software version ${hello.softwareVersion} is not supported.")))
        }
      }

      case unknownMessage => stop(FSM.Failure(new Exception(s"First message from device should be ${classOf[Hello].getSimpleName}, not ${unknownMessage.getClass.getSimpleName}.")))
    }
  }

  protected def deserialize(messageBody: Array[Byte]): MessageFormDeviceMarker = deserializers.deserialize(messageBody)

  protected def analyzeIncomingMessageFromIdentifiedDevice(dataFromDevice: ByteString, sd: IdentifiedStateData) = {
    log.debug(s"Received ${incomingMessage.messageBody.length} bytes from identified device.")

    val messageFormDevice = deserializers.deserialize(incomingMessage.messageBody).asInstanceOf[MessageFormDeviceMarker]
    log.debug(s"Received ${messageFormDevice.getClass.getSimpleName} message from device of runtimeId $runtimeId.")

    amaConfig.broadcaster ! new MessageFromDevice(runtimeId, messageFormDevice)

    stay using sd
  }*/

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