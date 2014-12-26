package as.sparkanta.actor.tcp.serversocket

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ SupervisorStrategy, OneForOneStrategy, Props, ActorRef, Cancellable, FSM, Terminated }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import scala.collection.mutable.ListBuffer
import as.sparkanta.server.message.{ StopListeningAt, ListenAt, ListenAtSuccessResult, ListenAtErrorResult, ListenAtResult }
import as.ama.addon.lifecycle.ShutdownSystem
import scala.net.IdentifiedInetSocketAddress
import scala.collection.mutable.Set

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

  case class ServerSocketRecord(listenAddress: IdentifiedInetSocketAddress, serverSocketHandler: ActorRef, keepServerSocketOpenTimeoutInSeconds: Int, var keepOpenServerSocketTimeout: Cancellable) extends Serializable

  val staticIdsCurrentlyOnline = Set[Long]()
}

class ServerSocketManager(
  amaConfig: AmaConfig
) extends FSM[ServerSocketManager.State, ServerSocketManager.StateData] {

  import ServerSocketManager._

  protected val taskBuffer = ListBuffer[(ListenAt, ActorRef)]()
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

  protected def listenAt(listenAt: ListenAt, listenAtResultListener: ActorRef) = openedServerSockets.find(ssr => ssr.listenAddress.id == listenAt.listenAddress.id) match {

    case Some(ssr) => {
      log.debug(s"Server socket ${ssr.listenAddress} is already open, resetting 'keep server socket opened timeout' (${ssr.keepServerSocketOpenTimeoutInSeconds} seconds).")

      ssr.keepOpenServerSocketTimeout.cancel()
      ssr.keepOpenServerSocketTimeout = createOpenedServerSocketTimeout(ssr.keepServerSocketOpenTimeoutInSeconds, ssr.listenAddress)

      listenAtResultListener ! new ListenAtSuccessResult(listenAt)

      stay using WaitingForOpeningServerSocketRequestStateData
    }

    case None => goto(OpeningServerSocket) using startServerSocketHandler(listenAt, listenAtResultListener)
  }

  protected def createOpenedServerSocketTimeout(keepServerSocketOpenTimeoutInSeconds: Int, listenAddress: IdentifiedInetSocketAddress): Cancellable = {
    val stopListeningAt = new StopListeningAt(listenAddress.id)
    context.system.scheduler.scheduleOnce(keepServerSocketOpenTimeoutInSeconds seconds, amaConfig.broadcaster, stopListeningAt)(context.dispatcher)
  }

  protected def startServerSocketHandler(listenAt: ListenAt, listenAtResultListener: ActorRef): OpeningServerSocketStateData = {

    log.debug(s"Trying to open server socket at ${listenAt.listenAddress} (with ${listenAt.openingServerSocketTimeoutInSeconds} seconds timeout).")

    import context.dispatcher
    val openingServerSocketTimeout = context.system.scheduler.scheduleOnce(listenAt.openingServerSocketTimeoutInSeconds seconds, self, OpeningServerSocketTimeout)

    val serverSocketHandler = {
      val props = Props(new ServerSocketHandler(amaConfig, listenAt, self, staticIdsCurrentlyOnline))
      context.actorOf(props, name = classOf[ServerSocketHandler].getSimpleName + "-" + listenAt.listenAddress.id)
    }

    serverSocketHandler ! true

    new OpeningServerSocketStateData(listenAt, listenAtResultListener, openingServerSocketTimeout, serverSocketHandler)
  }

  protected def addTaskToDo(listenAt: ListenAt, listenAtResultListener: ActorRef, sd: OpeningServerSocketStateData) = {
    taskBuffer += Tuple2(listenAt, listenAtResultListener)
    stay using sd
  }

  protected def analyzeListenAtResult(lar: ListenAtResult, sd: OpeningServerSocketStateData) = {

    sd.openingServerSocketTimeout.cancel()

    if (lar.isInstanceOf[ListenAtSuccessResult]) {

      log.debug(s"Successfully bind to ${lar.listenAt.listenAddress}, setting 'keep server socket opened timeout' to ${lar.listenAt.keepServerSocketOpenTimeoutInSeconds} seconds.")

      val lasr = lar.asInstanceOf[ListenAtSuccessResult]

      val keepOpenServerSocketTimeout = createOpenedServerSocketTimeout(lar.listenAt.keepServerSocketOpenTimeoutInSeconds, lar.listenAt.listenAddress)
      openedServerSockets += new ServerSocketRecord(lar.listenAt.listenAddress, sd.serverSocketHandler, lar.listenAt.keepServerSocketOpenTimeoutInSeconds, keepOpenServerSocketTimeout)
      context.watch(sd.serverSocketHandler)
    }

    sd.listenAtResultListener ! lar

    pickupNextTaskOrGotoFirstState
  }

  protected def pickupNextTaskOrGotoFirstState = taskBuffer.headOption match {
    case Some(taskToDo) => {
      taskBuffer -= taskToDo
      stay using startServerSocketHandler(taskToDo._1, taskToDo._2)
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