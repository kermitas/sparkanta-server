package as.sparkanta.actor.device1

import as.sparkanta.actor.tcp.socket.Socket

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, FSM, Cancellable }
import akka.util.{ FSMSuccessOrStop, InternalMessage }
import as.sparkanta.gateway.{ Device => DeviceSpec }
import as.sparkanta.device.{ DeviceIdentification, DeviceInfo }
import as.sparkanta.device.message.fromdevice.{ DeviceIdentification => DeviceIdentificationMessage }
import as.sparkanta.actor.speedtest.SpeedTest
import as.akka.broadcaster.Broadcaster

object DeviceWorker {

  lazy final val extraTimeForSpeedTestResultInMs = 250 // TODO move to config

  sealed trait State extends Serializable
  case object WaitingForDeviceIdentification extends State
  case object WaitingForSpeedTestResult extends State
  case object Identified extends State

  sealed trait StateData extends Serializable
  case class WaitingForDeviceIdentificationStateData(timeout: Cancellable) extends StateData
  case class WaitingForSpeedTestResultStateData(deviceIdentification: DeviceIdentification, timeout: Cancellable) extends StateData
  case class IdentifiedStateData(deviceInfo: DeviceInfo) extends StateData

  object Initialize extends InternalMessage
  object DeviceIdentificationTimeout extends InternalMessage
  object SpeedTestTimeout extends InternalMessage

  class StoppedByStopRequestedException(val stop: DeviceSpec.Stop, val stopSender: ActorRef) extends Exception
  class StoppedByStoppedSockedException(val listeningStopType: Socket.ListeningStopType) extends Exception
}

class DeviceWorker(
  start:       DeviceSpec.Start,
  startSender: ActorRef,
  broadcaster: ActorRef,
  deviceActor: ActorRef
) extends FSM[DeviceWorker.State, DeviceWorker.StateData] with FSMSuccessOrStop[DeviceWorker.State, DeviceWorker.StateData] {

  import DeviceWorker._
  import context.dispatcher

  {
    val timeout = context.system.scheduler.scheduleOnce(start.deviceIdentificationTimeoutInMs millis, self, DeviceIdentificationTimeout)
    startWith(WaitingForDeviceIdentification, new WaitingForDeviceIdentificationStateData(timeout))
  }

  when(WaitingForDeviceIdentification) {
    case Event(Initialize, _) => successOrStopWithFailure { init }
    case Event(a: Socket.ListenAtResult, sd: WaitingForDeviceIdentificationStateData) => successOrStopWithFailure { socketListenAtResult(a, sd) }
    case Event(a: DeviceIdentificationMessage, sd: WaitingForDeviceIdentificationStateData) => successOrStopWithFailure { deviceIdentification(a, sd) }
    case Event(DeviceIdentificationTimeout, _) => successOrStopWithFailure { deviceIdentificationTimeout }
  }

  when(WaitingForSpeedTestResult) {
    case Event(a: SpeedTest.SpeedTestResult, sd: WaitingForSpeedTestResultStateData) => successOrStopWithFailure { speedTestResult(a, sd) }
    case Event(SpeedTestTimeout, _) => successOrStopWithFailure { speedTestTimeout }
  }

  when(Identified) {
    case Event(true, _) => stay using stateData // nothing to do in this state
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(_: Socket.NewData, _)          => stay using stateData // nothing to do
    case Event(_: Socket.ListeningStarted, _) => stay using stateData // nothing to do
    case Event(a: Socket.ListeningStopped, _) => successOrStopWithFailure { socketListeningStopped(a) }
    case Event(a: DeviceSpec.Stop, _)         => successOrStopWithFailure { stopRequest(a, sender) }

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

  override def preStart(): Unit = {
    broadcaster ! new Broadcaster.Register(self, new DeviceWorkerClassifier(start.connectionInfo.remote.id, broadcaster))
  }

  protected def init = try {
    broadcaster ! new Socket.ListenAt(start.connectionInfo, start.akkaSocketTcpActor, start.maximumQueuedMessagesToSend)
    stay using stateData
  } catch {
    case e: Exception => {
      val startErrorResult = new DeviceSpec.StartErrorResult(e, start, startSender)
      startErrorResult.reply(deviceActor)
      stop(FSM.Failure(e))
    }
  }

  protected def socketListenAtResult(listenAtResultMessage: Socket.ListenAtResult, sd: WaitingForDeviceIdentificationStateData) = try {
    sd.timeout.cancel

    listenAtResultMessage match {
      case listenAtSuccessResult: Socket.ListenAtSuccessResult => {
        val startSuccessResult = new DeviceSpec.StartSuccessResult(start, startSender)
        val started = new DeviceSpec.Started(start, startSender)

        startSuccessResult.reply(deviceActor)

        deviceActor ! started
        started.reply(deviceActor)

        stay using stateData
      }

      case listenAtErrorResult: Socket.ListenAtErrorResult => stop(FSM.Failure(listenAtErrorResult.exception))
    }
  } catch {
    case e: Exception => {
      val startErrorResult = new DeviceSpec.StartErrorResult(e, start, startSender)
      startErrorResult.reply(deviceActor)
      stop(FSM.Failure(e))
    }
  }

  protected def deviceIdentification(deviceIdentificationMessage: DeviceIdentificationMessage, sd: WaitingForDeviceIdentificationStateData) = {
    sd.timeout.cancel

    // TODO see if device of this unique id (and name?? <- probably unique name will be removed at all) is not currently online

    start.pingPongSpeedTestTimeInMs match {
      case Some(pingPongSpeedTestTimeInMs) => {
        broadcaster ! new SpeedTest.StartSpeedTest(start.connectionInfo.remote.id, pingPongSpeedTestTimeInMs)
        val timeout = context.system.scheduler.scheduleOnce(pingPongSpeedTestTimeInMs + extraTimeForSpeedTestResultInMs millis, self, SpeedTestTimeout)
        goto(WaitingForSpeedTestResult) using new WaitingForSpeedTestResultStateData(deviceIdentificationMessage, timeout)
      }

      case None => gotoInitialized(deviceIdentificationMessage, None)
    }
  }

  protected def gotoInitialized(deviceIdentification: DeviceIdentification, pingPongsCountInTimeInMs: Option[(Long, Long)]) = {
    val deviceInfo = new DeviceInfo(start.connectionInfo, deviceIdentification, pingPongsCountInTimeInMs)

    val identifiedDeviceUp = new DeviceSpec.IdentifiedDeviceUp(deviceInfo, start, startSender)
    identifiedDeviceUp.reply(deviceActor)

    goto(Identified) using new IdentifiedStateData(deviceInfo)
  }

  protected def deviceIdentificationTimeout = stop(FSM.Failure(new Exception(s"Device identification (${start.deviceIdentificationTimeoutInMs} milliseconds) timeout reached.")))

  protected def speedTestTimeout = stop(FSM.Failure(new Exception(s"Speed test result (${start.pingPongSpeedTestTimeInMs.get + extraTimeForSpeedTestResultInMs} milliseconds) timeout reached.")))

  protected def stopRequest(stopMessage: DeviceSpec.Stop, stopSender: ActorRef) = stop(FSM.Failure(new StoppedByStopRequestedException(stopMessage, stopSender)))

  protected def speedTestResult(speedTestResultMessage: SpeedTest.SpeedTestResult, sd: WaitingForSpeedTestResultStateData) = {
    sd.timeout.cancel

    speedTestResultMessage match {
      case speedTestSuccessResult: SpeedTest.SpeedTestSuccessResult => gotoInitialized(sd.deviceIdentification, Some((speedTestSuccessResult.pingPongsCount, start.pingPongSpeedTestTimeInMs.get)))
      case speedTestErrorResult: SpeedTest.SpeedTestErrorResult     => stop(FSM.Failure(speedTestErrorResult.exception))
    }
  }

  protected def socketListeningStopped(listeningStopped: Socket.ListeningStopped) =
    stop(FSM.Failure(new StoppedByStoppedSockedException(listeningStopped.listeningStopType)))

  protected def terminate(reason: FSM.Reason, currentState: DeviceWorker.State, stateData: DeviceWorker.StateData): Unit = {
    //val exception = 
    reason match {
      case FSM.Normal => {
        log.debug(s"Stopping (normal), state $currentState, data $stateData.")
        //None
      }

      case FSM.Shutdown => {
        log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
        //Some(new Exception(s"${getClass.getSimpleName} actor was shutdown."))
      }

      case FSM.Failure(cause) => {
        log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")

        //cause match {
        //  case e: Exception => Some(e)
        //  case u            => Some(new Exception(s"Unknown stop cause of type ${u.getClass.getSimpleName}, $u."))
        //}
      }
    }

    broadcaster ! new Socket.StopListeningAt(start.connectionInfo.remote.id)

    val stopType = DeviceSpec.StoppedByRemoteSide // TODO stop type is not supported yet, need some work to transform socket stop type to this type !!

    if (stateData.isInstanceOf[IdentifiedStateData]) {
      val identifiedStateData = stateData.asInstanceOf[IdentifiedStateData]

      val identifiedDeviceDown = new DeviceSpec.IdentifiedDeviceDown(identifiedStateData.deviceInfo, stopType, identifiedStateData.deviceInfo.timeInSystemInMillis, start, startSender)
      identifiedDeviceDown.reply(deviceActor)
    }

    val stopped = new DeviceSpec.Stopped(stopType, start, startSender)
    deviceActor ! stopped
    stopped.reply(deviceActor)
  }
}
