package as.sparkanta.server.message

import scala.net.IdentifiedInetSocketAddress

class ListenAt(
  val listenAddress:                        IdentifiedInetSocketAddress,
  val openingServerSocketTimeoutInSeconds:  Int,
  val keepServerSocketOpenTimeoutInSeconds: Int,
  val forwardToRestAddress:                 IdentifiedInetSocketAddress
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(listenAddress=$listenAddress,openingServerSocketTimeoutInSeconds=$openingServerSocketTimeoutInSeconds,keepServerSocketOpenTimeoutInSeconds=$keepServerSocketOpenTimeoutInSeconds,forwardToRestAddress=$forwardToRestAddress)"

}

sealed abstract class ListenAtResult(val listenAt: ListenAt, val exception: Option[Exception]) extends Serializable

class ListenAtSuccessResult(listenAt: ListenAt) extends ListenAtResult(listenAt, None)

class ListenAtErrorResult(listenAt: ListenAt, exception: Exception) extends ListenAtResult(listenAt, Some(exception))