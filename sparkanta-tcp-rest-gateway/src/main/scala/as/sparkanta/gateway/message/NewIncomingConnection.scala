package as.sparkanta.gateway.message

import scala.net.IdentifiedInetSocketAddress
import java.net.InetSocketAddress

class NewIncomingConnection(
  val remoteAddress: InetSocketAddress,
  val localAddress:  IdentifiedInetSocketAddress
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(remoteAddress=$remoteAddress,localAddress=$localAddress)"

}
