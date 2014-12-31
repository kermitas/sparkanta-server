package as.sparkanta.actor2.tcp.serversocket

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ FSM, ActorRef }
import akka.io.{ IO, Tcp }

object ServerSocketWorker {
  sealed trait State extends Serializable
  case object Binding extends State
  case object Bound extends State

  sealed trait StateData extends Serializable
  case object BindingStateData extends StateData
  case object BoundStateData extends StateData
}

class ServerSocketWorker(listenAt: ServerSocket.ListenAt, listenAtSender: ActorRef, publishReplyOnBroadcaster: Boolean, broadcaster: ActorRef, serverSocket: ActorRef) extends FSM[ServerSocketWorker.State, ServerSocketWorker.StateData] {

  import ServerSocketWorker._
  import context.system

  startWith(Binding, BindingStateData)

  when(Binding, stateTimeout = listenAt.openingServerSocketTimeoutInMs millis) {

    case Event(_: Tcp.Bound, BindingStateData) => boundSuccessfully

    case Event(StateTimeout, BindingStateData) => bindingTimeout
  }

  when(Bound) {
    case Event(true, BoundStateData) => stay using stateData
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

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    IO(Tcp) ! Tcp.Bind(self, listenAt.listenAddress)
  }

  protected def boundSuccessfully = {
    stay using stateData
  }

  protected def bindingTimeout = {
    val exception = new Exception(s"Binding timeout (${listenAt.openingServerSocketTimeoutInMs} milliseconds).")
    val listenAtResult = new ServerSocket.ListenAtResult(Some(exception), listenAt, listenAtSender)

    listenAtSender ! listenAtResult
    if (publishReplyOnBroadcaster) broadcaster ! listenAtResult

    stop(FSM.Failure(exception))
  }

  protected def terminate(reason: FSM.Reason, currentState: ServerSocketWorker.State, stateData: ServerSocketWorker.StateData): Unit = {
    reason match {
      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData.")
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")
      }
    }
  }
}