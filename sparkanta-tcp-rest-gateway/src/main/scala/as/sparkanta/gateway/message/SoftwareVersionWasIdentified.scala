package as.sparkanta.gateway.message

import java.net.InetSocketAddress
import akka.actor.ActorRef

class SoftwareVersionWasIdentified(
  val softwareVersion:           Int,
  remoteAddress:                 InetSocketAddress,
  localAddress:                  InetSocketAddress,
  runtimeId:                     Long,
  tcpActor:                      ActorRef,
  tcpConnectionHandler:          ActorRef,
  val incomingDataListenerActor: ActorRef,
  val outgoingDataListenerActor: ActorRef
) extends NewIncomingConnection(remoteAddress, localAddress, runtimeId, tcpActor, tcpConnectionHandler)
