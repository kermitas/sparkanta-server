package as.sparkanta.server.message

class ListenAt(
  val listenIp:                             String,
  val listenPort:                           Int,
  val openingServerSocketTimeoutInSeconds:  Int,
  val keepServerSocketOpenTimeoutInSeconds: Int,
  val forwardToRestIp:                      String,
  val forwardToRestPort:                    Int
) extends Serializable

sealed abstract class ListenAtResult(val listenAt: ListenAt, val exception: Option[Exception]) extends Serializable

class ListenAtSuccessResult(listenAt: ListenAt) extends ListenAtResult(listenAt, None)

class ListenAtErrorResult(listenAt: ListenAt, exception: Exception) extends ListenAtResult(listenAt, Some(exception))