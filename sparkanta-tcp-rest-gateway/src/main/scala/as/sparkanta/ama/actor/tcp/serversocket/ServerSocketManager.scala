package as.sparkanta.ama.actor.tcp.serversocket

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ SupervisorStrategy, OneForOneStrategy, Props, ActorRef, Cancellable, FSM, Terminated }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable.ListBuffer
import as.sparkanta.server.message.{ StopListeningAt, ListenAt, ListenAtSuccessResult, ListenAtErrorResult, ListenAtResult }
import as.ama.addon.lifecycle.ShutdownSystem

object ServerSocketManager {
  sealed trait State extends Serializable
  case object WaitingForOpeningServerSocketRequest extends State
  case object OpeningServerSocket extends State

  sealed trait StateData extends Serializable
  case object WaitingForOpeningServerSocketRequestStateData extends StateData
  case class OpeningServerSocketStateData(listenAt: ListenAt, listenAtResultListener: ActorRef, openingServerSocketTimeout: Cancellable, serverSocketHandler: ActorRef) extends StateData

  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  case object OpeningServerSocketTimeout extends InternalMessage

  case class ServerSocketRecord(listenIp: String, listenPort: Int, serverSocketHandler: ActorRef, keepServerSocketOpenTimeoutInSeconds: Int, var keepOpenServerSocketTimeout: Cancellable) extends Serializable
}

class ServerSocketManager(
  amaConfig:             AmaConfig,
  serverSocketNumerator: AtomicLong,
  runtimeIdNumerator:    AtomicLong
) extends FSM[ServerSocketManager.State, ServerSocketManager.StateData] {

  def this(amaConfig: AmaConfig) = this(amaConfig, new AtomicLong(0), new AtomicLong(0))

  import ServerSocketManager._

  protected val tasksToDo = ListBuffer[(ListenAt, ActorRef)]()
  protected val openedServerSockets = ListBuffer[ServerSocketRecord]()

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  startWith(WaitingForOpeningServerSocketRequest, WaitingForOpeningServerSocketRequestStateData)

  when(WaitingForOpeningServerSocketRequest) {
    case Event(la: ListenAt, WaitingForOpeningServerSocketRequestStateData) => listenAt(la, sender())
  }

  when(OpeningServerSocket) {
    case Event(la: ListenAt, sd: OpeningServerSocketStateData)               => addTaskToDo(la, sender(), sd)
    case Event(lar: ListenAtResult, sd: OpeningServerSocketStateData)        => analyzeListenAtResult(lar, sd)
    case Event(OpeningServerSocketTimeout, sd: OpeningServerSocketStateData) => openingServerSocketTimeout(sd)
  }

  onTransition {
    case fromState -> toState => log.info(s"State change from $fromState to $toState")
  }

  whenUnhandled {
    case Event(Terminated(deadWatchedActor), stateData) => {
      removeFromOpenedServerSocketList(deadWatchedActor)
      stay using stateData
    }

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
    try {
      // notifying broadcaster to register us with given classifier
      amaConfig.broadcaster ! new Broadcaster.Register(self, new ServerSocketManagerClassifier)

      amaConfig.sendInitializationResult()
    } catch {
      case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
    }
  }

  protected def removeFromOpenedServerSocketList(serverSocketActor: ActorRef) =
    openedServerSockets.find(_.serverSocketHandler == serverSocketActor).map(openedServerSockets -= _)

  protected def listenAt(listenAt: ListenAt, listenAtResultListener: ActorRef) = openedServerSockets.find(oss => oss.listenPort == listenAt.listenPort && oss.listenIp.equals(listenAt.listenIp)) match {

    case Some(ssr) => {
      log.debug(s"Server socket ${ssr.listenIp}:${ssr.listenPort} is already open, resetting 'keep open server socket timeout' (${ssr.keepServerSocketOpenTimeoutInSeconds} seconds).")

      ssr.keepOpenServerSocketTimeout.cancel()
      ssr.keepOpenServerSocketTimeout = createOpenedServerSocketTimeout(ssr.keepServerSocketOpenTimeoutInSeconds, ssr.listenIp, ssr.listenPort)

      listenAtResultListener ! new ListenAtSuccessResult(listenAt)

      stay using WaitingForOpeningServerSocketRequestStateData
    }

    case None => goto(OpeningServerSocket) using startServerSocket(listenAt, listenAtResultListener)
  }

  protected def createOpenedServerSocketTimeout(keepServerSocketOpenTimeoutInSeconds: Int, listenIp: String, listenPort: Int): Cancellable = {
    val stopListeningAt = new StopListeningAt(listenIp, listenPort)
    context.system.scheduler.scheduleOnce(keepServerSocketOpenTimeoutInSeconds seconds, amaConfig.broadcaster, stopListeningAt)(context.dispatcher)
  }

  protected def startServerSocket(listenAt: ListenAt, listenAtResultListener: ActorRef): OpeningServerSocketStateData = {

    log.debug(s"Trying to open server socket at ${listenAt.listenIp}:${listenAt.listenPort} with timeout of ${listenAt.openingServerSocketTimeoutInSeconds} seconds.")

    import context.dispatcher
    val openingServerSocketTimeout = context.system.scheduler.scheduleOnce(listenAt.openingServerSocketTimeoutInSeconds seconds, self, OpeningServerSocketTimeout)

    val serverSocketHandler = {
      val props = Props(new ServerSocketHandler(amaConfig, runtimeIdNumerator, listenAt, self))
      context.actorOf(props, name = classOf[ServerSocketHandler].getSimpleName + "-" + serverSocketNumerator.getAndIncrement)
    }

    serverSocketHandler ! true

    new OpeningServerSocketStateData(listenAt, listenAtResultListener, openingServerSocketTimeout, serverSocketHandler)
  }

  protected def addTaskToDo(listenAt: ListenAt, listenAtResultListener: ActorRef, sd: OpeningServerSocketStateData) = {
    tasksToDo += Tuple2(listenAt, listenAtResultListener)
    stay using sd
  }

  protected def analyzeListenAtResult(lar: ListenAtResult, sd: OpeningServerSocketStateData) = {

    sd.openingServerSocketTimeout.cancel()

    if (lar.isInstanceOf[ListenAtSuccessResult]) {

      log.debug(s"Successfully bind to ${lar.listenAt.listenIp}:${lar.listenAt.listenPort}, setting 'keep open server socket timeout' to ${lar.listenAt.keepServerSocketOpenTimeoutInSeconds} seconds.")

      val lasr = lar.asInstanceOf[ListenAtSuccessResult]

      val keepOpenServerSocketTimeout = createOpenedServerSocketTimeout(lar.listenAt.keepServerSocketOpenTimeoutInSeconds, lar.listenAt.listenIp, lar.listenAt.listenPort)
      openedServerSockets += new ServerSocketRecord(lar.listenAt.listenIp, lar.listenAt.listenPort, sd.serverSocketHandler, lar.listenAt.keepServerSocketOpenTimeoutInSeconds, keepOpenServerSocketTimeout)
      context.watch(sd.serverSocketHandler)
    }

    sd.listenAtResultListener ! lar

    pickupNextTaskOrGotoFirstState
  }

  protected def pickupNextTaskOrGotoFirstState = tasksToDo.headOption match {
    case Some(taskToDo) => {
      tasksToDo -= taskToDo
      stay using startServerSocket(taskToDo._1, taskToDo._2)
    }

    case None => goto(WaitingForOpeningServerSocketRequest) using WaitingForOpeningServerSocketRequestStateData
  }

  protected def openingServerSocketTimeout(sd: OpeningServerSocketStateData) = {
    sd.listenAtResultListener ! new ListenAtErrorResult(sd.listenAt, new Exception(s"Timeout (${sd.listenAt.openingServerSocketTimeoutInSeconds} seconds) while opening server socket."))
    context.stop(sd.serverSocketHandler)
    pickupNextTaskOrGotoFirstState
  }

  protected def terminate(reason: FSM.Reason, currentState: ServerSocketManager.State, stateData: ServerSocketManager.StateData): Unit = {
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

    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${classOf[ServerSocketManager].getSimpleName} is stopping.")))
  }
}