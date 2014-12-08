package as.sparkanta.gateway.message

import java.net.InetSocketAddress
import akka.actor.ActorRef

class NewIncomingConnection(
  val remoteAddress:        InetSocketAddress,
  val localAddress:         InetSocketAddress,
  val runtimeId:            Long,
  val tcpActor:             ActorRef,
  val tcpConnectionHandler: ActorRef
) extends Serializable
