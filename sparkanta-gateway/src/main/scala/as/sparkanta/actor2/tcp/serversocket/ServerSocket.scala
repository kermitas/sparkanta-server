package as.sparkanta.actor2.tcp.serversocket

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.io.{ IO, Tcp }
import scala.util.Try
//import as.ama.util.FromBroadcaster
import akka.actor.{ ActorRef, FSM, InvalidActorNameException, Actor, ActorLogging, Props, Cancellable, OneForOneStrategy, SupervisorStrategy, Terminated }
//import akka.util.{ ReplyOn1Impl, ReplyOn2Impl }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import scala.net.{ IdentifiedInetSocketAddress, IdentifiedConnectionInfo }
import scala.collection.mutable.ListBuffer
import java.util.concurrent.atomic.AtomicLong
import as.ama.addon.lifecycle.ShutdownSystem
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message, OutgoingReplyOn2Message }
import as.akka.broadcaster.MessageWithSender
import scala.collection.mutable.Map

object ServerSocket {
  sealed trait State extends Serializable
  case object WaitingForOpeningServerSocketRequest extends State
  case object OpeningServerSocket extends State

  sealed trait StateData extends Serializable
  case object WaitingForOpeningServerSocketRequestStateData extends StateData
  case class OpeningServerSocketStateData(listenAt: ListenAt, listenAtResultListener: ActorRef, openingServerSocketTimeout: Cancellable, serverSocketWorker: ActorRef) extends StateData

  /*
  sealed trait Message extends Serializable
  sealed trait InternalMessage extends Message
  case object OpeningServerSocketTimeout extends InternalMessage

  case class ServerSocketRecord(listenAddress: IdentifiedInetSocketAddress, serverSocketHandler: ActorRef, keepServerSocketOpenTimeoutInSeconds: Int, var keepOpenServerSocketTimeout: Cancellable) extends Serializable
  */

  /*
  trait Message extends Serializable
  trait IncomingMessage extends Message
  class IncomingReplyableMessage(var replyAlsoOn: Option[ActorRef] = None) extends Message
  trait InternalMessage extends IncomingMessage
  trait OutgoingMessage extends Message
  class OutgoingReplyOn1Message[T <: IncomingReplyableMessage](request1: MessageWithSender[T]) extends ReplyOn1Impl(request1) with OutgoingMessage {
    def reply(sender: ActorRef): Unit = {
      request1.messageSender.tell(this, sender)
      request1.message.replyAlsoOn.map(_.tell(this, sender))
    }
  }
  class OutgoingReplyOn2Message[T <: IncomingReplyableMessage, E](request1: MessageWithSender[T], request2: MessageWithSender[E]) extends ReplyOn2Impl(request1, request2) with OutgoingMessage {
    def reply(sender: ActorRef): Unit = {
      request1.messageSender.tell(this, sender)
      request1.message.replyAlsoOn.map(_.tell(this, sender))
    }
  }*/

  class ListenAt(val listenAddress: IdentifiedInetSocketAddress, val openingServerSocketTimeoutInMs: Long, val keepOpenForMs: Long) extends IncomingReplyableMessage
  //class ListenAtFromBroadcaster(listenAt: ListenAt) extends FromBroadcaster[ListenAt](listenAt) with IncomingMessage
  class StopListeningAt(val id: Long) extends IncomingReplyableMessage

  class ListenAtResult(val exception: Option[Exception], listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(new MessageWithSender(listenAt, listenAtSender)) //with OutgoingMessage
  class SuccessfulListenAtResult(listenAt: ListenAt, listenAtSender: ActorRef) extends ListenAtResult(None, listenAt, listenAtSender)
  class ErrorListenAtResult(exception: Exception, listenAt: ListenAt, listenAtSender: ActorRef) extends ListenAtResult(Some(exception), listenAt, listenAtSender)
  class ListeningStarted(listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(new MessageWithSender(listenAt, listenAtSender))
  class ListeningStopped(val exception: Option[Exception], listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(new MessageWithSender(listenAt, listenAtSender))

  //class StopListeningAtResult(val wasListening: Try[Boolean], stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends ReplyOn2Impl[StopListeningAt, ListenAt](stopListeningAt, stopListeningAtSender, listenAt, listenAtSender) with OutgoingMessage
  class StopListeningAtResult(val wasListening: Try[Boolean], stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn2Message[StopListeningAt, ListenAt](new MessageWithSender(stopListeningAt, stopListeningAtSender), new MessageWithSender(listenAt, listenAtSender))

  class NewConnection(val connectionInfo: IdentifiedConnectionInfo, val akkaSocketTcpActor: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends OutgoingReplyOn1Message(new MessageWithSender(listenAt, listenAtSender))

  //case object ServerSocketOpeningTimeout extends InternalMessage
  //class ServerSocketOpeningTimeout(val record: Record) extends InternalMessage
  //class KeepOpenedServerSocketTimeout(val record: Record) extends InternalMessage

  class Record(val id: Long, val serverSocketWorker: ActorRef)
}

class ServerSocket(
  amaConfig:                        AmaConfig,
  remoteConnectionsUniqueNumerator: AtomicLong
) extends Actor with ActorLogging {

  def this(amaConfig: AmaConfig) = this(amaConfig, new AtomicLong(0))

  import ServerSocket._

  protected val map = Map[Long, Record]()

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new ServerSocketClassifier(amaConfig.broadcaster))

    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {
    case listenAt: ListenAt => startListeningAt(listenAt, sender)
    //case a: StopListeningAt =>
    //case a: ListeningStarted => listeningStarted(a.request1.message.listenAddress.id)
    //case a: ListeningStopped =>
    //case Terminated(deadServerSocketWorker) => removeDeadServerSocketWorker(deadServerSocketWorker)

    case message            => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def startListeningAt(listenAt: ListenAt, listenAtSender: ActorRef): Unit = try {
    val props = Props(new ServerSocketWorker(listenAt, listenAtSender, self, remoteConnectionsUniqueNumerator, amaConfig.broadcaster))
    context.actorOf(props, name = classOf[ServerSocketWorker].getSimpleName + "-" + listenAt.listenAddress.id)
  } catch {
    case e: InvalidActorNameException =>
  }

  /*
  protected def listeningStarted(id: Long, serverSocketWorker: ActorRef): Unit = {
    map.put(listenAt.listenAddress.id, new Record())
  }*/

  /*
  protected def removeDeadServerSocketWorker(deadServerSocketWorker: ActorRef): Unit = {
    map.values.find(_.serverSocketWorker == deadServerSocketWorker).map(record => map.remove(record.id))
  }*/
}

/*
class ServerSocket(
  amaConfig:                        AmaConfig,
  remoteConnectionsUniqueNumerator: AtomicLong
) extends FSM[ServerSocket.State, ServerSocket.StateData] {

  def this(amaConfig: AmaConfig) = this(amaConfig, new AtomicLong(0))

  import ServerSocket._
  import context.dispatcher

  protected val taskBuffer = ListBuffer[(ListenAt, ActorRef, Boolean)]()
  protected val openedServerSockets = ListBuffer[Record]()

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  startWith(WaitingForOpeningServerSocketRequest, WaitingForOpeningServerSocketRequestStateData)

  when(WaitingForOpeningServerSocketRequest) {
    case Event(a: ListenAtFromBroadcaster, WaitingForOpeningServerSocketRequestStateData) => listenAt(a.message, sender(), true)
    case Event(a: ListenAt, WaitingForOpeningServerSocketRequestStateData)                => listenAt(a, sender(), false)
  }

  when(OpeningServerSocket) {
    case Event(a: ListenAtFromBroadcaster, sd: OpeningServerSocketStateData) => addTaskToDo(a.message, sender(), sd, true)
    case Event(la: ListenAt, sd: OpeningServerSocketStateData)               => addTaskToDo(la, sender(), sd, false)
    case Event(lar: ListenAtResult, sd: OpeningServerSocketStateData)        => analyzeListenAtResult(lar, sd)
    case Event(ServerSocketOpeningTimeout, sd: OpeningServerSocketStateData) => openingServerSocketTimeout(sd)
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
  override def preStart(): Unit = try {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new ServerSocketClassifier)

    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  protected def removeFromOpenedServerSocketList(serverSocketWorker: ActorRef) =
    openedServerSockets.find(_.serverSocketWorker == serverSocketWorker).map(openedServerSockets -= _)

  protected def listenAt(listenAt: ListenAt, listenAtResultListener: ActorRef, publishReplyOnBroadcaster: Boolean) = openedServerSockets.find(_.listenAt.listenAddress.id == listenAt.listenAddress.id) match {

    case Some(record) => {
      log.debug(s"Server socket ${record.listenAt.listenAddress} is already open, resetting 'keep server socket opened timeout' (${record.listenAt.keepOpenForMs} milliseconds).")

      record.keepOpenedServerSocketTimeout.cancel
      record.keepOpenedServerSocketTimeout = createOpenedServerSocketTimeout(record.listenAt.keepOpenForMs, record.listenAt.listenAddress)

      val listenAtSuccessResult = new ListenAtResult(None, listenAt, listenAtResultListener)

      listenAtResultListener ! listenAtSuccessResult
      if (publishReplyOnBroadcaster) amaConfig.broadcaster ! listenAtSuccessResult

      stay using WaitingForOpeningServerSocketRequestStateData
    }

    case None => goto(OpeningServerSocket) using startServerSocketHandler(listenAt, listenAtResultListener, publishReplyOnBroadcaster)
  }

  protected def createOpenedServerSocketTimeout(keepOpenForMs: Long, listenAddress: IdentifiedInetSocketAddress): Cancellable = {
    val stopListeningAt = new StopListeningAt(listenAddress.id)
    context.system.scheduler.scheduleOnce(keepOpenForMs millis, amaConfig.broadcaster, stopListeningAt)
  }

  protected def startServerSocketHandler(listenAt: ListenAt, listenAtResultListener: ActorRef, publishReplyOnBroadcaster: Boolean): OpeningServerSocketStateData = {

    log.debug(s"Trying to open server socket at ${listenAt.listenAddress} (with ${listenAt.openingServerSocketTimeoutInMs} milliseconds timeout).")
    //case Event(a: ListenAtFromBroadcaster, WaitingForOpeningServerSocketRequestStateData) => listenAt(a.message, sender(), true)

    import context.dispatcher
    val openingServerSocketTimeout = context.system.scheduler.scheduleOnce(listenAt.openingServerSocketTimeoutInMs millis, self, ServerSocketOpeningTimeout)

    /*val serverSocketWorker = {
      val props = Props(new ServerSocketHandler(amaConfig, listenAt, remoteConnectionsUniqueNumerator))
      context.actorOf(props, name = classOf[ServerSocketHandler].getSimpleName + "-" + listenAt.listenAddress.id)
    }*/

    val serverSocketWorker = null

    new OpeningServerSocketStateData(listenAt, listenAtResultListener, openingServerSocketTimeout, serverSocketWorker)
  }

  protected def addTaskToDo(listenAt: ListenAt, listenAtResultListener: ActorRef, sd: OpeningServerSocketStateData, publishReplyOnBroadcaster: Boolean) = {
    taskBuffer += new Tuple3(listenAt, listenAtResultListener, publishReplyOnBroadcaster)
    stay using sd
  }

  protected def analyzeListenAtResult(lar: ListenAtResult, sd: OpeningServerSocketStateData) = {

    sd.openingServerSocketTimeout.cancel()

    if (lar.exception.isEmpty) {

      log.debug(s"Successfully bound to ${lar.request1.message.listenAddress}, setting 'keep server socket opened timeout' to ${lar.request1.message.keepOpenForMs} milliseconds.")

      val keepOpenServerSocketTimeout = createOpenedServerSocketTimeout(lar.request1.message.keepOpenForMs, lar.request1.message.listenAddress)
      openedServerSockets += new Record(lar.listenAt.listenAddress, sd.serverSocketWorker, lar.listenAt.keepServerSocketOpenTimeoutInSeconds, keepOpenServerSocketTimeout)
      context.watch(sd.serverSocketWorker)
    }

    sd.listenAtResultListener ! lar

    pickupNextTaskOrGotoFirstState
  }

  protected def pickupNextTaskOrGotoFirstState = taskBuffer.headOption match {
    case Some(taskToDo) => {
      taskBuffer -= taskToDo
      stay using startServerSocketHandler(taskToDo._1, taskToDo._2, taskToDo._3)
    }

    case None => goto(WaitingForOpeningServerSocketRequest) using WaitingForOpeningServerSocketRequestStateData
  }

  protected def openingServerSocketTimeout(sd: OpeningServerSocketStateData) = {
    val listenAtErrorResult = new ListenAtErrorResult(sd.listenAt, new Exception(s"Timeout (${sd.listenAt.openingServerSocketTimeoutInSeconds} seconds) while opening server socket."))
    sd.listenAtResultListener ! listenAtErrorResult
    context.stop(sd.serverSocketWorker)
    pickupNextTaskOrGotoFirstState
  }

  protected def terminate(reason: FSM.Reason, currentState: ServerSocket.State, stateData: ServerSocket.StateData): Unit = {
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
*/

/*
object ServerSocket {
  sealed trait State extends Serializable
  case object WaitingForOpeningServerSocketRequest extends State
  case object OpeningServerSocket extends State

  sealed trait StateData extends Serializable
  case object WaitingForOpeningServerSocketRequestStateData extends StateData
  case class OpeningServerSocketStateData(record: Record, publishReplyOnBroadcaster: Boolean, openingServerSocketTimeout: Cancellable) extends StateData

  trait Message extends Serializable
  trait IncomingMessage extends Message
  trait InternalMessage extends IncomingMessage
  trait OutgoingMessage extends Message

  class ListenAt(val listenAddress: IdentifiedInetSocketAddress, val openingServerSocketTimeoutInMs: Long, val keepOpenForMs: Long) extends IncomingMessage
  class ListenAtFromBroadcaster(listenAt: ListenAt) extends FromBroadcaster[ListenAt](listenAt) with IncomingMessage
  class StopListeningAt(val id: Long) extends IncomingMessage

  class ListenAtResult(val exception: Option[Exception], listenAt: ListenAt, listenAtSender: ActorRef) extends ReplyOn1Impl[ListenAt](listenAt, listenAtSender) with OutgoingMessage
  class ListeningStarted(listenAt: ListenAt, listenAtSender: ActorRef) extends ReplyOn1Impl[ListenAt](listenAt, listenAtSender) with OutgoingMessage
  class ListeningStopped(val exception: Option[Exception], listenAt: ListenAt, listenAtSender: ActorRef) extends ReplyOn1Impl[ListenAt](listenAt, listenAtSender) with OutgoingMessage
  class StopListeningAtResult(val wasListening: Boolean, stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends ReplyOn2Impl[StopListeningAt, ListenAt](stopListeningAt, stopListeningAtSender, listenAt, listenAtSender) with OutgoingMessage
  class NewConnection(val connectionInfo: IdentifiedConnectionInfo, listenAt: ListenAt, listenAtSender: ActorRef) extends ReplyOn1Impl[ListenAt](listenAt, listenAtSender) with OutgoingMessage

  class ServerSocketOpeningTimeout(val record: Record) extends InternalMessage
  class KeepOpenedServerSocketTimeout(val record: Record) extends InternalMessage

  class Record(var listenAt: ListenAt, var listenAtResultListener: ActorRef, var keepOpenedServerSocketTimeout: Option[Cancellable])
}

class ServerSocket(
  amaConfig:                        AmaConfig,
  remoteConnectionsUniqueNumerator: AtomicLong
) extends FSM[ServerSocket.State, ServerSocket.StateData] {

  def this(amaConfig: AmaConfig) = this(amaConfig, new AtomicLong(0))

  import ServerSocket._
  import context.system
  import context.dispatcher

  protected val taskBuffer = ListBuffer[(IncomingMessage, ActorRef)]()
  protected val openedServerSockets = new ListBuffer[Record]()

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  startWith(WaitingForOpeningServerSocketRequest, WaitingForOpeningServerSocketRequestStateData)

  when(WaitingForOpeningServerSocketRequest) {
    case Event(a: ListenAtFromBroadcaster, _)       => listenAt(a.message, sender, true)
    case Event(a: ListenAt, _)                      => listenAt(a, sender, false)
    case Event(a: StopListeningAt, _)               => stopListeningAt(a, sender, None)
    case Event(a: ServerSocketOpeningTimeout, _)    => stay using stateData
    case Event(a: KeepOpenedServerSocketTimeout, _) => keepOpenedServerSocketTimeout(a.record)
  }

  when(OpeningServerSocket) {
    case Event(a: ListenAtFromBroadcaster, sd: OpeningServerSocketStateData)       => addToTaskBuffer(a, sender, sd)
    case Event(a: ListenAt, sd: OpeningServerSocketStateData)                      => addToTaskBuffer(a, sender, sd)
    case Event(a: StopListeningAt, sd: OpeningServerSocketStateData)               => addToTaskBuffer(a, sender, sd)
    case Event(a: ServerSocketOpeningTimeout, sd: OpeningServerSocketStateData)    => serverSocketOpeningTimeout(a.record, sd)
    case Event(a: KeepOpenedServerSocketTimeout, sd: OpeningServerSocketStateData) => addToTaskBuffer(a, sender, sd)
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

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new ServerSocketClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  protected def listenAt(listenAt: ListenAt, listenAtSender: ActorRef, publishReplyOnBroadcaster: Boolean) = openedServerSockets.find(_.listenAt.listenAddress.id == listenAt.listenAddress.id) match {
    case Some(record) => try {
      require(record.listenAt.listenAddress.ip.equals(listenAt.listenAddress.ip) && record.listenAt.listenAddress.port == listenAt.listenAddress.port, s"Incoming request of id ${listenAt.listenAddress.id} to listen at ${listenAt.listenAddress} does not match currently bound ${record.listenAt.listenAddress}.")

      record.keepOpenedServerSocketTimeout.map(_.cancel)

      record.listenAt = listenAt
      record.listenAtResultListener = listenAtSender
      record.keepOpenedServerSocketTimeout = Some(context.system.scheduler.scheduleOnce(listenAt.keepOpenForMs millis, self, new KeepOpenedServerSocketTimeout(record)))

      stay using WaitingForOpeningServerSocketRequestStateData
    } catch {
      case e: Exception => stopListeningAt(new StopListeningAt(listenAt.listenAddress.id), listenAtSender, Some(e))
    }

    case None => try {
      IO(Tcp) ! Tcp.Bind(self, listenAt.listenAddress)
      val record = new Record(listenAt, listenAtSender, None)
      val openingServerSocketTimeout = context.system.scheduler.scheduleOnce(listenAt.openingServerSocketTimeoutInMs millis, self, new ServerSocketOpeningTimeout(record))

      goto(OpeningServerSocket) using new OpeningServerSocketStateData(record, publishReplyOnBroadcaster, openingServerSocketTimeout)
    } catch {
      case e: Exception => {
        val listenAtResult = new ListenAtResult(Some(e), listenAt, listenAtSender)
        listenAtSender ! listenAtResult
        if (publishReplyOnBroadcaster) amaConfig.broadcaster ! listenAtResult
        stay using WaitingForOpeningServerSocketRequestStateData
      }
    }
  }

  protected def stopListeningAt(stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef, exception: Option[Exception]) = {
    // TODO
    stay using WaitingForOpeningServerSocketRequestStateData
  }

  protected def serverSocketOpeningTimeout(record: Record, sd: OpeningServerSocketStateData) = {
    // TODO
    // TODO take next or go to initial state
    stay using sd
  }

  protected def keepOpenedServerSocketTimeout(record: Record) = {

    //a.record

    openedServerSockets -= record

    IO(Tcp) ! Tcp.Close()

    stay using WaitingForOpeningServerSocketRequestStateData
  }

  protected def addToTaskBuffer(incomingMessage: IncomingMessage, incomingMessageSender: ActorRef, sd: OpeningServerSocketStateData) = {
    taskBuffer += new Tuple2(incomingMessage, incomingMessageSender)
    stay using sd
  }

  protected def terminate(reason: FSM.Reason, currentState: ServerSocket.State, stateData: ServerSocket.StateData): Unit = {
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
*/
