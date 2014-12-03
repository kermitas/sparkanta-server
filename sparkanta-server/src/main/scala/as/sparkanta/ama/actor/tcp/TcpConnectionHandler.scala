package as.sparkanta.ama.actor.tcp

import akka.actor.{ Actor, ActorLogging }
import java.net.InetSocketAddress
import as.sparkanta.ama.config.AmaConfig
import akka.io.Tcp
import Tcp._

class TcpConnectionHandler(amaConfig: AmaConfig, remoteAddress: InetSocketAddress, localAddress: InetSocketAddress) extends Actor with ActorLogging {
  override def receive = {

    case Received(data) => sender() ! Write(data)

    case PeerClosed     => context stop self

    case message        => log.warning(s"Unhandled $message send by ${sender()}")
  }
}
