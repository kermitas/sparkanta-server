package as.sparkanta.actor.tcp.socket

import akka.actor.{ ActorLogging, ActorRef, Actor }
import as.sparkanta.gateway.NetworkDeviceInfo

// TODO change to FSM !!

class SocketHandler(val networkDeviceInfo: NetworkDeviceInfo, val tcpActor: ActorRef) extends Actor with ActorLogging {
  override def receive = {
    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }
}
