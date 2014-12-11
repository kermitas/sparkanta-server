package as.sparkanta.server.message

import scala.net.IdentifiedInetSocketAddress

class StopListeningAt(
  val listenAddressId: Long
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(listenAddressId=$listenAddressId)"

}
