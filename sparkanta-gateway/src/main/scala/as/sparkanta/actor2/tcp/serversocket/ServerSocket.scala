package as.sparkanta.actor2.tcp.serversocket

import scala.language.postfixOps
import scala.concurrent.duration._
import as.ama.util.FromBroadcaster
import akka.actor.{ ActorRef, ActorLogging, Actor, Cancellable }
import akka.util.{ ReplyOn1Impl, ReplyOn2Impl }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import scala.net.{ IdentifiedInetSocketAddress, IdentifiedConnectionInfo }

object ServerSocket {
  trait Message extends Serializable
  trait IncomingMessage extends Message
  trait OutgoingMessage extends Message

  class ListenAt(val listenAddress: IdentifiedInetSocketAddress, val keepOpenForMs: Long) extends IncomingMessage
  class ListenAtFromBroadcaster(listenAt: ListenAt) extends FromBroadcaster[ListenAt](listenAt) with IncomingMessage
  class StopListeningAt(val id: Long) extends IncomingMessage

  class ListenAtResult(val exception: Option[Exception], listenAt: ListenAt, listenAtSender: ActorRef) extends ReplyOn1Impl[ListenAt](listenAt, listenAtSender) with OutgoingMessage
  class ListeningStarted(listenAt: ListenAt, listenAtSender: ActorRef) extends ReplyOn1Impl[ListenAt](listenAt, listenAtSender) with OutgoingMessage
  class ListeningStopped(val exception: Option[Exception], listenAt: ListenAt, listenAtSender: ActorRef) extends ReplyOn1Impl[ListenAt](listenAt, listenAtSender) with OutgoingMessage
  class StopListeningAtResult(stopListeningAt: StopListeningAt, stopListeningAtSender: ActorRef, listenAt: ListenAt, listenAtSender: ActorRef) extends ReplyOn2Impl[StopListeningAt, ListenAt](stopListeningAt, stopListeningAtSender, listenAt, listenAtSender) with OutgoingMessage
  class NewConnection(val connectionInfo: IdentifiedConnectionInfo, listenAt: ListenAt, listenAtSender: ActorRef) extends ReplyOn1Impl[ListenAt](listenAt, listenAtSender) with OutgoingMessage
}

class ServerSocket(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import ServerSocket._

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new ServerSocketClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }

}
