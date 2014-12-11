package as.sparkanta.server.message

import scala.net.IdentifiedInetSocketAddress

class StopListeningAt(
  val listenAddress: IdentifiedInetSocketAddress
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(listenAddress=$listenAddress)"

}
