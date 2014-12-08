package as.sparkanta.gateway.message

import java.net.InetSocketAddress
import akka.actor.ActorRef
import akka.util.ByteString

class DataFromDevice(
  val data:                      ByteString,
  val softwareVersion:           Int,
  val remoteAddress:             InetSocketAddress,
  val localAddress:              InetSocketAddress,
  val runtimeId:                 Long,
  val tcpActor:                  ActorRef,
  val tcpConnectionHandler:      ActorRef,
  val incomingDataListenerActor: ActorRef,
  val outgoingDataListenerActor: ActorRef
) extends Serializable