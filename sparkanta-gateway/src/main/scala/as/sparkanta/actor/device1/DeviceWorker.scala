package as.sparkanta.actor.device1

import akka.actor.{ ActorRef, FSM }
import akka.util.{ FSMSuccessOrStop, InternalMessage }
import as.sparkanta.gateway.{ Device => DeviceSpec }

object DeviceWorker {
  sealed trait State extends Serializable
  case object A extends State
  case object B extends State

  sealed trait StateData extends Serializable
  case object AStateData extends StateData
  case object BStateData extends StateData

  object Initialize extends InternalMessage

  //class StopRequestedException(val stop: DeviceSpec.Stop, val stopSender: ActorRef) extends Exception
  class StopRequestedException(val stop: DeviceSpec.Stop, val stopSender: ActorRef) extends Exception
}

class DeviceWorker(start: DeviceSpec.Start, startSender: ActorRef, broadcaster: ActorRef, deviceActor: ActorRef) extends FSM[DeviceWorker.State, DeviceWorker.StateData] with FSMSuccessOrStop[DeviceWorker.State, DeviceWorker.StateData] {

  import DeviceWorker._

  startWith(A, AStateData)

  when(A) {
    case Event(Initialize, sd) => stay using sd
  }

  when(B) {
    case Event(true, sd) => stay using sd
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
  self ! Initialize

  protected def terminate(reason: FSM.Reason, currentState: DeviceWorker.State, stateData: DeviceWorker.StateData): Unit = {
    val exception = reason match {
      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData.")
        None
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
        Some(new Exception(s"${getClass.getSimpleName} actor was shutdown."))
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")

        cause match {
          case e: Exception => Some(e)
          case u            => Some(new Exception(s"Unknown stop cause of type ${u.getClass.getSimpleName}, $u."))
        }
      }
    }

    /*
    val stopType = exception match {
      case Some(e) =>
      case None    => new Exception("Stopped")
    }
    val stopped = new DeviceSpec.Stopped(start, startSender)
    */
  }
}
