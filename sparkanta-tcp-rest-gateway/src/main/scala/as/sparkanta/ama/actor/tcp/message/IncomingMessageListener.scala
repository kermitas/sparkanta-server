package as.sparkanta.ama.actor.tcp.message

import akka.actor.{ Actor, ActorRef, FSM, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import java.net.InetSocketAddress
import as.sparkanta.ama.actor.tcp.connection.TcpConnectionHandler
import akka.io.Tcp
import Tcp._
import akka.util.{ ByteString, FSMSuccessOrStop }

object IncomingMessageListener {
  sealed trait State extends Serializable
  case object Unidentified extends State
  case object Identified extends State

  sealed trait StateData extends Serializable
  case object UnidentifiedStateData extends StateData
  case class IdentifiedStateData(deviceId: String, identificationTimeInMs: Long) extends StateData

  sealed trait Message extends Serializable
  sealed trait OutgoingMessage extends Message
  class DeviceIsDown(val deviceId: String, val timeInSystemInMs: Long, val runtimeId: Long) extends OutgoingMessage
}

class IncomingMessageListener(
  amaConfig:     AmaConfig,
  remoteAddress: InetSocketAddress,
  localAddress:  InetSocketAddress,
  tcpActor:      ActorRef,
  runtimeId:     Long
) extends FSM[IncomingMessageListener.State, IncomingMessageListener.StateData] with FSMSuccessOrStop[IncomingMessageListener.State, IncomingMessageListener.StateData] {

  import IncomingMessageListener._

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Terminating because once of child actors failed.", t)))
      SupervisorStrategy.Escalate
    }
  }

  startWith(Unidentified, UnidentifiedStateData)

  when(Unidentified) {
    case Event(im: TcpConnectionHandler.IncomingMessage, UnidentifiedStateData) => successOrStopWithFailure { analyzeIncomingMessageFromUnidentifiedDevice(im) }
  }

  when(Identified) {
    case Event(im: TcpConnectionHandler.IncomingMessage, sd: IdentifiedStateData) => successOrStopWithFailure { analyzeIncomingMessageFromIdentifiedDevice(im, sd) }
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

  protected def analyzeIncomingMessageFromUnidentifiedDevice(incomingMessage: TcpConnectionHandler.IncomingMessage) = {
    log.debug(s"Received ${incomingMessage.messageBody.length} bytes from unidentified device.")

    // TODO: to remove
    incomingMessage.tcpActor ! Write(ByteString(incomingMessage.messageBody))

    // TODO: deserialize (from bytes into object) and it should be Hello message that will contains device id
    // TODO: if yes then spawn child actor OutgoingMessageListener and pass to it tcpActor, deviceId
    // TODO: register on child actors death
    // TODO: if not then throw exception

    goto(Identified) using new IdentifiedStateData("abcdefghij", System.currentTimeMillis)
  }

  protected def analyzeIncomingMessageFromIdentifiedDevice(incomingMessage: TcpConnectionHandler.IncomingMessage, sd: IdentifiedStateData) = {
    log.debug(s"Received ${incomingMessage.messageBody.length} bytes from device '${sd.deviceId}'.")

    // TODO: to remove
    incomingMessage.tcpActor ! Write(ByteString(incomingMessage.messageBody))

    // TODO: deserialize (from bytes into object) and publish on broadcaster wrapped into some object that will contains deviceId and runtimeId

    stay using sd
  }

  protected def terminate(reason: FSM.Reason, currentState: IncomingMessageListener.State, stateData: IncomingMessageListener.StateData): Unit = {

    val deviceId = stateData match {
      case IdentifiedStateData(deviceId, identificationTimeInMs) => {
        amaConfig.broadcaster ! new DeviceIsDown(deviceId, System.currentTimeMillis - identificationTimeInMs, runtimeId)
        Some(deviceId)
      }
      case _ => None
    }

    val deviceIdMessage = deviceId.map(deviceId => s", device id '$deviceId'.").getOrElse(".")

    reason match {

      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData$deviceIdMessage")
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData$deviceIdMessage")
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData$deviceIdMessage")
      }
    }
  }
}