package as.sparkanta.gateway.message

import scala.net.IdentifiedInetSocketAddress

class NewIncomingConnection(
  val remoteAddress: IdentifiedInetSocketAddress,
  val localAddress:  IdentifiedInetSocketAddress
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(remoteAddress=$remoteAddress,localAddress=$localAddress)"

}
