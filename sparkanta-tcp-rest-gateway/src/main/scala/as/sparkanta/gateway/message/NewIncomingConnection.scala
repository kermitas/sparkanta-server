package as.sparkanta.gateway.message

import java.net.InetSocketAddress
import akka.actor.ActorRef

class NewIncomingConnection(
  val remoteAddress:             InetSocketAddress,
  val localAddress:              InetSocketAddress,
  val tcpConnectionHandlerActor: ActorRef,
  val tcpActor:                  ActorRef,
  val runtimeId:                 Long
) extends Serializable
