package as.sparkanta.actor2.tcp.serversocket

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.net.{ IdentifiedInetSocketAddress, IdentifiedConnectionInfo }
import akka.actor.{ Cancellable, FSM, ActorRef }
import akka.io.{ IO, Tcp }
import akka.util.FSMSuccessOrStop
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

object ServerSocketWorker {
  sealed trait State extends Serializable
  case object Binding extends State
  case object Bound extends State

  sealed trait StateData extends Serializable
  case object BindingStateData extends StateData
  case class BoundStateData(val akkaServerSocketTcpActor: ActorRef, var keepOpenedServerSocketTimeout: Cancellable) extends StateData

  trait Message extends Serializable
  trait IncomingMessage extends Message
  trait InternalMessage extends IncomingMessage
  object KeepOpenedServerSocketTimeout extends InternalMessage
}

class ServerSocketWorker(
  var listenAt:                     ServerSocket.ListenAt,
  var listenAtSender:               ActorRef,
  serverSocket:                     ActorRef,
  remoteConnectionsUniqueNumerator: AtomicLong,
  broadcaster:                      ActorRef
) extends FSM[ServerSocketWorker.State, ServerSocketWorker.StateData] with FSMSuccessOrStop[ServerSocketWorker.State, ServerSocketWorker.StateData] {

  import ServerSocketWorker._
  import context.system
  import context.dispatcher

  startWith(Binding, BindingStateData)

  when(Binding, stateTimeout = listenAt.openingServerSocketTimeoutInMs millis) {
    case Event(_: Tcp.Bound, BindingStateData)                   => successOrStopWithFailure { boundSuccessfully(sender) }
    case Event(Tcp.CommandFailed(_: Tcp.Bind), BindingStateData) => successOrStopWithFailure { boundFailed }
    case Event(StateTimeout, BindingStateData)                   => successOrStopWithFailure { bindingTimeout }
  }

  when(Bound) {
    case Event(a: ServerSocket.ListenAt, sd: BoundStateData)        => successOrStopWithFailure { renewListenAt(a, sender, sd) }
    case Event(a: ServerSocket.StopListeningAt, sd: BoundStateData) => successOrStopWithFailure { stopListening(a, sender, sd) }
    case Event(Tcp.Connected(remote, _), sd: BoundStateData)        => successOrStopWithFailure { newIncomingConnection(remote, sender, sd) }
    case Event(KeepOpenedServerSocketTimeout, sd: BoundStateData)   => successOrStopWithFailure { keepOpenedServerSocketTimeout(sd) }
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
    IO(Tcp) ! Tcp.Bind(self, listenAt.listenAddress)
  }

  protected def boundSuccessfully(akkaServerSocketTcpActor: ActorRef) = {
    log.debug(s"Successfully bound to ${listenAt.listenAddress}, setting close timeout for ${listenAt.keepOpenForMs} milliseconds.")

    val keepOpenedServerSocketTimeout = context.system.scheduler.scheduleOnce(listenAt.keepOpenForMs millis, self, KeepOpenedServerSocketTimeout)

    val successfulListenAtResult = new ServerSocket.SuccessfulListenAtResult(false, listenAt, listenAtSender)
    val listeningStarted = new ServerSocket.ListeningStarted(listenAt, listenAtSender)

    successfulListenAtResult.reply(serverSocket)
    serverSocket ! listeningStarted
    listeningStarted.reply(serverSocket)

    goto(Bound) using new BoundStateData(akkaServerSocketTcpActor, keepOpenedServerSocketTimeout)
  }

  protected def boundFailed = {
    val exception = new Exception(s"Bind to ${listenAt.listenAddress} failed.")
    stop(FSM.Failure(exception))
  }

  protected def bindingTimeout = {
    val exception = new Exception(s"Binding timeout (${listenAt.openingServerSocketTimeoutInMs} milliseconds).")
    stop(FSM.Failure(exception))
  }

  protected def renewListenAt(newListenAt: ServerSocket.ListenAt, newListenAtSender: ActorRef, sd: BoundStateData) = try {

    require(newListenAt.listenAddress.id == listenAt.listenAddress.id, s"Renewing listening request id ${newListenAt.listenAddress.id} should match already bound ${listenAt.listenAddress.id}.")
    require(newListenAt.listenAddress.ip.equals(listenAt.listenAddress.ip), s"Renewing listening request ip ${newListenAt.listenAddress.ip} should match already bound ${listenAt.listenAddress.ip}.")
    require(newListenAt.listenAddress.port == listenAt.listenAddress.port, s"Renewing listening request port ${newListenAt.listenAddress.port} should match already bound ${listenAt.listenAddress.port}.")

    log.debug(s"Resetting close timeout (previous ${listenAt.keepOpenForMs} milliseconds, new one ${newListenAt.keepOpenForMs} milliseconds) for ${listenAt.listenAddress}.")

    sd.keepOpenedServerSocketTimeout.cancel
    sd.keepOpenedServerSocketTimeout = context.system.scheduler.scheduleOnce(newListenAt.keepOpenForMs millis, self, KeepOpenedServerSocketTimeout)

    val successfulListenAtResult = new ServerSocket.SuccessfulListenAtResult(true, newListenAt, newListenAtSender)
    successfulListenAtResult.reply(serverSocket)

    listenAt = newListenAt
    listenAtSender = newListenAtSender

    stay using sd
  } catch {
    case iae: IllegalArgumentException => {
      log.warning(iae.getMessage)

      val errorListenAtResult = new ServerSocket.ErrorListenAtResult(iae, newListenAt, newListenAtSender)
      errorListenAtResult.reply(serverSocket)

      stay using sd
    }
  }

  protected def newIncomingConnection(remoteAddress: InetSocketAddress, akkaSocketTcpActor: ActorRef, sd: BoundStateData): State =
    newIncomingConnection(new IdentifiedInetSocketAddress(remoteConnectionsUniqueNumerator.getAndIncrement, remoteAddress.getHostString, remoteAddress.getPort), akkaSocketTcpActor, sd)

  protected def newIncomingConnection(remoteAddress: IdentifiedInetSocketAddress, akkaSocketTcpActor: ActorRef, sd: BoundStateData): State =
    newIncomingConnection(new IdentifiedConnectionInfo(remoteAddress, listenAt.listenAddress), akkaSocketTcpActor, sd)

  protected def newIncomingConnection(connectionInfo: IdentifiedConnectionInfo, akkaSocketTcpActor: ActorRef, sd: BoundStateData): State = {
    log.debug(s"New incoming connection $connectionInfo.")
    val newConnection = new ServerSocket.NewConnection(connectionInfo, akkaSocketTcpActor, listenAt, listenAtSender)
    newConnection.reply(serverSocket)
    stay using sd
  }

  protected def keepOpenedServerSocketTimeout(sd: BoundStateData) = {
    val exception = new Exception(s"Keeping server socket open timeout (${listenAt.keepOpenForMs} milliseconds).")
    stop(FSM.Failure(exception))
  }

  protected def stopListening(stopListeningAt: ServerSocket.StopListeningAt, stopListeningAtSender: ActorRef, sd: BoundStateData) = try {

    require(stopListeningAt.id == listenAt.listenAddress.id, s"Stop listening request id ${stopListeningAt.id} should match ${listenAt.listenAddress.id}.")

    log.debug(s"Stopping listening at ${listenAt.listenAddress}.")

    sd.keepOpenedServerSocketTimeout.cancel

    val successStopListeningAtResult = new ServerSocket.SuccessStopListeningAtResult(true, stopListeningAt, stopListeningAtSender, listenAt, listenAtSender)
    successStopListeningAtResult.reply(serverSocket)

    stop(FSM.Normal)
  } catch {
    case iae: IllegalArgumentException => {
      log.warning(iae.getMessage)

      val errorStopListeningAtResult = new ServerSocket.ErrorStopListeningAtResult(iae, stopListeningAt, stopListeningAtSender, listenAt, listenAtSender)
      errorStopListeningAtResult.reply(sender)

      stay using sd
    }
  }

  protected def terminate(reason: FSM.Reason, currentState: ServerSocketWorker.State, stateData: ServerSocketWorker.StateData): Unit = {

    reason match {
      case FSM.Normal         => log.debug(s"Stopping (normal), state $currentState, data $stateData.")
      case FSM.Shutdown       => log.debug(s"Stopping (shutdown), state $currentState, data $stateData.")
      case FSM.Failure(cause) => log.warning(s"Stopping (failure, cause $cause), state $currentState, data $stateData.")
    }

    currentState match {
      case Binding => {

        val exception = reason match {
          case FSM.Normal   => new Exception(s"${getClass.getSimpleName} actor was stopped normally.")
          case FSM.Shutdown => new Exception(s"${getClass.getSimpleName} actor was shutdown.")
          case FSM.Failure(cause) => cause match {
            case e: Exception => e
            case u            => new Exception(s"${getClass.getSimpleName} actor was stopped, cause type ${u.getClass.getSimpleName}, cause $u.")
          }
        }

        val errorListenAtResult = new ServerSocket.ErrorListenAtResult(exception, listenAt, listenAtSender)
        errorListenAtResult.reply(serverSocket)
      }

      case Bound => {
        val boundStateData = stateData.asInstanceOf[BoundStateData]
        boundStateData.akkaServerSocketTcpActor ! Tcp.Close // is it needed?

        val exception = reason match {
          case FSM.Failure(cause) => cause match {
            case e: Exception => Some(e)
            case u            => Some(new Exception(s"${getClass.getSimpleName} actor was stopped, cause type ${u.getClass.getSimpleName}, cause $u."))
          }

          case _ => None
        }

        val listeningStopped = new ServerSocket.ListeningStopped(exception, listenAt, listenAtSender)
        serverSocket ! listeningStopped
        listeningStopped.reply(serverSocket)
      }
    }
  }
}