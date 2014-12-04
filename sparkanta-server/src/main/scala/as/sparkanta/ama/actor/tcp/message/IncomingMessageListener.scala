package as.sparkanta.ama.actor.tcp.message

import akka.actor.{ Actor, ActorRef, FSM, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import java.net.InetSocketAddress
import as.sparkanta.ama.actor.tcp.connection.TcpConnectionHandler
import akka.io.Tcp
import Tcp._
import akka.util.ByteString

object IncomingMessageListener {
  sealed trait State extends Serializable
  case object Unidentified extends State
  case object Identified extends State

  sealed trait StateData extends Serializable
  case object UnidentifiedStateData extends StateData
  case class IdentifiedStateData(deviceId: String, identificationTimeInMs: Long) extends StateData

  sealed trait Message extends Serializable
  sealed trait OutgoingMessage extends Message
  class DeviceIsDown(val deviceId: String, timeInSystemInMs: Long) extends OutgoingMessage
}

class IncomingMessageListener(
  amaConfig:     AmaConfig,
  remoteAddress: InetSocketAddress,
  localAddress:  InetSocketAddress,
  tcpActor:      ActorRef
) extends FSM[IncomingMessageListener.State, IncomingMessageListener.StateData] {

  import IncomingMessageListener._

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Escalate
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
    amaConfig.broadcaster ! new Broadcaster.Register(self, new IncomingMessageListenerClassifier(context.parent))
  }

  /**
   * Use this method to surround initialization, for example:
   *
   * when(Initializing) {
   *   case Event(true, InitializingStateData) => successOrStop { ... }
   * }
   */
  protected def successOrStopWithFailure(f: => State): State = try {
    f
  } catch {
    case e: Exception => stop(FSM.Failure(e))
  }

  protected def analyzeIncomingMessageFromUnidentifiedDevice(incomingMessage: TcpConnectionHandler.IncomingMessage) = {
    log.debug(s"Received ${incomingMessage.messageBody.length} bytes.")

    // TODO: to remove
    incomingMessage.tcpActor ! Write(ByteString(incomingMessage.messageBody))

    // TODO: deserialize (from bytes into object) and it should be Hello message that will contains device id
    // TODO: if yes then spawn child actor OutgoingMessageListener and pass to it tcpActor, deviceId
    // TODO: register on child actors deatch

    goto(Identified) using new IdentifiedStateData("abcdefghij", System.currentTimeMillis)
  }

  protected def analyzeIncomingMessageFromIdentifiedDevice(incomingMessage: TcpConnectionHandler.IncomingMessage, sd: IdentifiedStateData) = {
    log.debug(s"Received ${incomingMessage.messageBody.length} bytes from device ${sd.deviceId}.")

    // TODO: to remove
    incomingMessage.tcpActor ! Write(ByteString(incomingMessage.messageBody))

    // TODO: deserialize (from bytes into object)  and publish on broadcaster

    stay using sd
  }

  protected def terminate(reason: FSM.Reason, currentState: IncomingMessageListener.State, stateData: IncomingMessageListener.StateData): Unit = {

    val deviceId = stateData match {
      case IdentifiedStateData(deviceId, identificationTimeInMs) => {
        amaConfig.broadcaster ! new DeviceIsDown(deviceId, System.currentTimeMillis - identificationTimeInMs)
        Some(deviceId)
      }
      case _ => None
    }

    val deviceIdMessage = deviceId.map(id => s", device id $id.").getOrElse(".")

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