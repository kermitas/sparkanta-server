package as.sparkanta.gateway.message

import scala.net.IdentifiedInetSocketAddress

class ConnectionClosed(
  val throwable:          Option[Throwable],
  val closedByRemoteSide: Boolean,
  val softwareVersion:    Option[Int],
  val remoteAddress:      IdentifiedInetSocketAddress,
  val localAddress:       IdentifiedInetSocketAddress
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(throwable=$throwable,closedByRemoteSide=$closedByRemoteSide,softwareVersion=$softwareVersion,remoteAddress=$remoteAddress,localAddress=$localAddress)"

}