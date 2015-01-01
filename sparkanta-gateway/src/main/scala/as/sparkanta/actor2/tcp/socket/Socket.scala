package as.sparkanta.actor2.tcp.socket

import akka.actor.ActorRef
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message, OutgoingReplyOn2Message }
import scala.net.IdentifiedConnectionInfo

object Socket {
  class ListenAt(val connectionInfo: IdentifiedConnectionInfo, val akkaSocketTcpActor: ActorRef) extends IncomingReplyableMessage
}

class Socket {
  import Socket._
}
