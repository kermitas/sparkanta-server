package as.sparkanta.actor.device

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, FSM, Cancellable, OneForOneStrategy, SupervisorStrategy, Terminated, ActorRefFactory, Props }
import akka.util.{ FSMSuccessOrStop, InternalMessage }
import as.sparkanta.gateway.{ Device => DeviceSpec }
import as.sparkanta.device.{ DeviceIdentification, DeviceInfo }
import as.sparkanta.device.message.fromdevice.{ DeviceIdentification => DeviceIdentificationMessage }
import as.sparkanta.actor.speedtest.SpeedTest
import as.akka.broadcaster.Broadcaster
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.device.message.todevice.{ GatewayHello, ServerHello }
import as.sparkanta.gateway.NoAck
import as.sparkanta.actor.device.inactivity.InactivityMonitor
import as.sparkanta.actor.device.message.deserializer.Deserializer
import as.sparkanta.actor.device.message.serializer.Serializer

object DeviceWorker {
  sealed trait State extends Serializable
  case object WaitingForDeviceIdentification extends State
  case object WaitingForSpeedTestResult extends State
  case object Identified extends State

  sealed trait StateData extends Serializable
  case class WaitingForDeviceIdentificationStateData(timeout: Cancellable, var isStarted: Boolean = false) extends StateData
  case class WaitingForSpeedTestResultStateData(deviceIdentification: DeviceIdentification, timeout: Cancellable) extends StateData
  case class IdentifiedStateData(deviceInfo: DeviceInfo) extends StateData

  object Initialize extends InternalMessage
  object DeviceIdentificationTimeout extends InternalMessage
  object SpeedTestTimeout extends InternalMessage

  class StoppedByStopRequestedException(val stop: DeviceSpec.Stop, val stopSender: ActorRef) extends Exception
  class StoppedByStoppedSockedException(val listeningStopType: Socket.ListeningStopType) extends Exception

  def startActor(
    actorRefFactory: ActorRefFactory,
    start:           DeviceSpec.Start,
    startSender:     ActorRef,
    broadcaster:     ActorRef,
    deviceActor:     ActorRef,
    config:          DeviceWorkerConfig
  ): ActorRef = {
    val props = Props(new DeviceWorker(start, startSender, broadcaster, deviceActor, config))
    val actor = actorRefFactory.actorOf(props, name = classOf[DeviceWorker].getSimpleName + "-" + start.connectionInfo.remote.id)
    broadcaster ! new Broadcaster.Register(actor, new DeviceWorkerClassifier(start.connectionInfo.remote.id, broadcaster))
    actor
  }
}

class DeviceWorker(
  start:       DeviceSpec.Start,
  startSender: ActorRef,
  broadcaster: ActorRef,
  deviceActor: ActorRef,
  config:      DeviceWorkerConfig
) extends FSM[DeviceWorker.State, DeviceWorker.StateData] with FSMSuccessOrStop[DeviceWorker.State, DeviceWorker.StateData] {

  import DeviceWorker._
  import context.dispatcher

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      stop(FSM.Failure(new Exception("Stopping because of problem in child actor.", t)))
      SupervisorStrategy.Stop
    }
  }

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
    case Event(_: DeviceSpec.SendMessageSuccessResult, _) => stay using stateData // do nothing
    case Event(a: DeviceSpec.SendMessageErrorResult, _)   => successOrStopWithFailure { sendMessageError(a) }
  }

  onTransition {
    case fromState -> toState => log.debug(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(_: Socket.NewData, _)          => stay using stateData // nothing to do
    case Event(_: Socket.ListeningStarted, _) => stay using stateData // nothing to do
    case Event(a: Socket.ListeningStopped, _) => successOrStopWithFailure { socketListeningStopped(a) }
    case Event(a: DeviceSpec.Stop, _)         => successOrStopWithFailure { stopRequest(a, sender) }
    case Event(Terminated(diedActor), _)      => successOrStopWithFailure { watchedActorWasTerminated(diedActor) }

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

  protected def init = {
    val deserializer = Deserializer.startActor(context, start.connectionInfo, broadcaster, self)
    val serializer = Serializer.startActor(context, start.connectionInfo.remote.id, broadcaster, self, start.maximumQueuedMessagesToSend, config.waitingForSendDataResultTimeoutInMsIfNotSetInAck)

    context.watch(deserializer)
    context.watch(serializer)

    broadcaster ! new Socket.ListenAt(start.connectionInfo, start.akkaSocketTcpActor, start.maximumQueuedMessagesToSend)
    stay using stateData
  }

  protected def socketListenAtResult(listenAtResultMessage: Socket.ListenAtResult, sd: WaitingForDeviceIdentificationStateData) = listenAtResultMessage match {
    case listenAtSuccessResult: Socket.ListenAtSuccessResult => {
      val startSuccessResult = new DeviceSpec.StartSuccessResult(start, startSender)
      val started = new DeviceSpec.Started(start, startSender)

      startSuccessResult.reply(deviceActor)
      started.reply(deviceActor)

      sd.isStarted = true

      stay using stateData
    }

    case listenAtErrorResult: Socket.ListenAtErrorResult => throw new Exception("Stopping because of problem with initializing socket actor.", listenAtErrorResult.exception)
  }

  protected def deviceIdentification(deviceIdentificationMessage: DeviceIdentificationMessage, sd: WaitingForDeviceIdentificationStateData) = {
    sd.timeout.cancel

    // TODO somehow check if device of this unique id is not currently online

    start.pingPongSpeedTestTimeInMs match {
      case Some(pingPongSpeedTestTimeInMs) => {
        broadcaster ! new SpeedTest.StartSpeedTest(start.connectionInfo.remote.id, pingPongSpeedTestTimeInMs)
        val timeout = context.system.scheduler.scheduleOnce(pingPongSpeedTestTimeInMs + config.extraTimeForWaitingOnSpeedTestResultInMs millis, self, SpeedTestTimeout)
        goto(WaitingForSpeedTestResult) using new WaitingForSpeedTestResultStateData(deviceIdentificationMessage, timeout)
      }

      case None => gotoInitialized(deviceIdentificationMessage, None)
    }
  }

  protected def gotoInitialized(deviceIdentification: DeviceIdentification, pingPongsCountInTimeInMs: Option[(Long, Long)]) = {
    val deviceInfo = new DeviceInfo(start.connectionInfo, deviceIdentification, pingPongsCountInTimeInMs)

    val inactivityMonitor = InactivityMonitor.startActor(context, deviceInfo.connectionInfo.remote.id, broadcaster, config.warningTimeAfterMs, config.inactivityTimeAfterMs)
    context.watch(inactivityMonitor)

    log.info(s"!!!!!!!!!!!!!! Device $deviceInfo successfully initialized and up. !!!!!!!!!!!!!!!!!")

    broadcaster ! new DeviceSpec.SendMessage(start.connectionInfo.remote.id, new GatewayHello, NoAck)

    broadcaster ! new DeviceSpec.SendMessage(start.connectionInfo.remote.id, new ServerHello, NoAck) // TODO ServerHello should be send by the server, not gateway

    val identifiedDeviceUp = new DeviceSpec.IdentifiedDeviceUp(deviceInfo, start, startSender)
    identifiedDeviceUp.reply(deviceActor)

    goto(Identified) using new IdentifiedStateData(deviceInfo)
  }

  protected def deviceIdentificationTimeout = stop(FSM.Failure(new Exception(s"Device identification (${start.deviceIdentificationTimeoutInMs} milliseconds) timeout reached.")))

  protected def speedTestTimeout = stop(FSM.Failure(new Exception(s"Speed test result (${start.pingPongSpeedTestTimeInMs.get + config.extraTimeForWaitingOnSpeedTestResultInMs} milliseconds) timeout reached.")))

  protected def stopRequest(stopMessage: DeviceSpec.Stop, stopSender: ActorRef) = {
    val stopSuccessResult = new DeviceSpec.StopSuccessResult(stopMessage, stopSender)
    stopSuccessResult.reply(deviceActor)

    stop(FSM.Failure(new StoppedByStopRequestedException(stopMessage, stopSender)))
  }

  protected def speedTestResult(speedTestResultMessage: SpeedTest.SpeedTestResult, sd: WaitingForSpeedTestResultStateData) = {
    sd.timeout.cancel

    speedTestResultMessage match {
      case speedTestSuccessResult: SpeedTest.SpeedTestSuccessResult => gotoInitialized(sd.deviceIdentification, Some((speedTestSuccessResult.pingPongsCount, start.pingPongSpeedTestTimeInMs.get)))
      case speedTestErrorResult: SpeedTest.SpeedTestErrorResult     => stop(FSM.Failure(speedTestErrorResult.exception))
    }
  }

  protected def socketListeningStopped(listeningStopped: Socket.ListeningStopped) =
    stop(FSM.Failure(new StoppedByStoppedSockedException(listeningStopped.listeningStopType)))

  protected def sendMessageError(sendMessageErrorResult: DeviceSpec.SendMessageErrorResult) =
    stop(FSM.Failure(new Exception(s"Stopping because of problem with sending message.", sendMessageErrorResult.exception)))

  protected def watchedActorWasTerminated(diedActor: ActorRef) =
    stop(FSM.Failure(new Exception(s"Stopping because watched actor $diedActor died.")))

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

    broadcaster ! new Socket.StopListeningAt(start.connectionInfo.remote.id)

    val enforcedException = exception.getOrElse(new Exception(s"Actor ${getClass.getSimpleName} stopped normally."))

    val stopType = enforcedException match {
      case a: StoppedByStopRequestedException => new DeviceSpec.StoppedBecauseOfLocalSideRequest(a.stop, a.stopSender)

      case a: StoppedByStoppedSockedException => a.listeningStopType match {
        case Socket.StoppedByRemoteSide                   => DeviceSpec.StoppedByRemoteSide
        case a: Socket.StoppedBecauseOfLocalSideException => new DeviceSpec.StoppedBecauseOfLocalSideException(a.exception)
        case a: Socket.StoppedBecauseOfLocalSideRequest   => new DeviceSpec.StoppedBecauseOfLocalSideException(new Exception(s"Stopped because of ${a.stopListeningAt.getClass.getSimpleName} message send by ${a.stopListeningAtSender} to socket actor."))
      }

      case e => new DeviceSpec.StoppedBecauseOfLocalSideException(e)
    }

    stateData match {
      case a: WaitingForDeviceIdentificationStateData => {
        a.timeout.cancel
        log.info(s"!!!!!!!!!!!!!! Not identified device ${start.connectionInfo} down.!!!!!!!!!!!!!!!!!")

        if (a.isStarted) {
          val stopped = new DeviceSpec.Stopped(stopType, start, startSender)
          stopped.reply(deviceActor)
        } else {
          val startErrorResult = new DeviceSpec.StartErrorResult(enforcedException, start, startSender)
          startErrorResult.reply(deviceActor)
        }
      }

      case a: WaitingForSpeedTestResultStateData => {
        a.timeout.cancel
        log.info(s"!!!!!!!!!!!!!! Not identified device ${start.connectionInfo} down.!!!!!!!!!!!!!!!!!")

        val stopped = new DeviceSpec.Stopped(stopType, start, startSender)
        stopped.reply(deviceActor)
      }

      case a: IdentifiedStateData => {
        log.info(s"!!!!!!!!!!!!!! Identified device ${a.deviceInfo} down.!!!!!!!!!!!!!!!!!")

        val identifiedDeviceDown = new DeviceSpec.IdentifiedDeviceDown(a.deviceInfo, stopType, a.deviceInfo.timeInSystemInMillis, start, startSender)
        identifiedDeviceDown.reply(deviceActor)

        val stopped = new DeviceSpec.Stopped(stopType, start, startSender)
        stopped.reply(deviceActor)
      }
    }
  }
}
