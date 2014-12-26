package as.sparkanta.gateway.message

import scala.net.IdentifiedInetSocketAddress
import java.net.InetSocketAddress

class ConnectionClosed(
  val throwable:          Option[Throwable],
  val closedByRemoteSide: Boolean,
  val remoteAddress:      InetSocketAddress,
  val localAddress:       IdentifiedInetSocketAddress
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(throwable=$throwable,closedByRemoteSide=$closedByRemoteSide,remoteAddress=$remoteAddress,localAddress=$localAddress)"

}