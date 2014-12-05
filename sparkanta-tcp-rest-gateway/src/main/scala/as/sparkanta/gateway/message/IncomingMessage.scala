package as.sparkanta.gateway.message

import java.net.InetSocketAddress
import akka.actor.ActorRef

class IncomingMessage(
  val remoteAddress: InetSocketAddress,
  val localAddress:  InetSocketAddress,
  val messageBody:   Array[Byte],
  val tcpActor:      ActorRef,
  val runtimeId:     Long
) extends Serializable