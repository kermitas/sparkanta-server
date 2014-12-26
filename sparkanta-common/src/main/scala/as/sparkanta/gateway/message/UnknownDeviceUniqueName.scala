package as.sparkanta.gateway.message

import java.net.InetSocketAddress
import scala.net.IdentifiedInetSocketAddress

class UnknownDeviceUniqueName(
  val unknownName:   String,
  val remoteAddress: InetSocketAddress,
  val localAddress:  IdentifiedInetSocketAddress
) extends ForwardToRestServer {

  override def toString = s"${getClass.getSimpleName}(unknownName=$unknownName,remoteAddress=$remoteAddress,localAddress=$localAddress)"

}