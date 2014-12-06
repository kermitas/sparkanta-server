package as.sparkanta.ama.actor.tcp.message

import akka.actor.{ ActorRef, FSM, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import java.net.InetSocketAddress
import akka.io.Tcp
import Tcp._
import akka.util.FSMSuccessOrStop
import as.sparkanta.device.message.{ MessageFormDevice => MessageFormDeviceMarker, Deserializators, Hello }
import as.sparkanta.gateway.message.{ DeviceIsDown, IncomingMessage }
import as.sparkanta.internal.message.MessageFromDevice

object IncomingMessageListener {
  sealed trait State extends Serializable
  case object Unidentified extends State
  case object Identified extends State

  sealed trait StateData extends Serializable
  case object UnidentifiedStateData extends StateData
  case class IdentifiedStateData(sparkDeviceId: String, softwareVersion: Int, identificationTimeInMs: Long) extends StateData
}

class IncomingMessageListener(
  amaConfig:     AmaConfig,
  remoteAddress: InetSocketAddress,
  localAddress:  InetSocketAddress,
  tcpActor:      ActorRef,
  runtimeId:     Long
) extends FSM[IncomingMessageListener.State, IncomingMessageListener.StateData] with FSMSuccessOrStop[IncomingMessageListener.State, IncomingMessageListener.StateData] {

  import IncomingMessageListener._

  protected val deserializators = new Deserializators

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Terminating because once of child actors failed.", t)))
      SupervisorStrategy.Escalate
    }
  }

  startWith(Unidentified, UnidentifiedStateData)

  when(Unidentified) {
    case Event(im: IncomingMessage, UnidentifiedStateData) => successOrStopWithFailure { analyzeIncomingMessageFromUnidentifiedDevice(im) }
  }

  when(Identified) {
    case Event(im: IncomingMessage, sd: IdentifiedStateData) => successOrStopWithFailure { analyzeIncomingMessageFromIdentifiedDevice(im, sd) }
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
    amaConfig.broadcaster ! new Broadcaster.Register(self, new IncomingMessageListenerClassifier(runtimeId))
  }

  protected def analyzeIncomingMessageFromUnidentifiedDevice(incomingMessage: IncomingMessage) = {
    log.debug(s"Received ${incomingMessage.messageBody.length} bytes from unidentified device.")

    deserializators.deserialize(incomingMessage.messageBody).asInstanceOf[MessageFormDeviceMarker] match {
      case hello: Hello => {
        log.debug(s"Device of runtimeId $runtimeId identified itself as sparkDeviceId ${hello.sparkDeviceId}, softwareVersion ${hello.softwareVersion}.")

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

  protected def isSoftwareVersionSupported(softwareVersion: Int): Boolean = true

  protected def analyzeIncomingMessageFromIdentifiedDevice(incomingMessage: IncomingMessage, sd: IdentifiedStateData) = {
    log.debug(s"Received ${incomingMessage.messageBody.length} bytes from identified device.")

    val messageFormDevice = deserializators.deserialize(incomingMessage.messageBody).asInstanceOf[MessageFormDeviceMarker]
    log.debug(s"Received ${messageFormDevice.getClass.getSimpleName} message from device of runtimeId $runtimeId.")

    amaConfig.broadcaster ! new MessageFromDevice(runtimeId, messageFormDevice)

    stay using sd
  }

  protected def terminate(reason: FSM.Reason, currentState: IncomingMessageListener.State, stateData: IncomingMessageListener.StateData): Unit = {

    val sparkDeviceId = stateData match {
      case IdentifiedStateData(sparkDeviceId, softwareVersion, identificationTimeInMs) => {
        amaConfig.broadcaster ! new DeviceIsDown(runtimeId, sparkDeviceId, System.currentTimeMillis - identificationTimeInMs)
        Some(sparkDeviceId)
      }
      case _ => None
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