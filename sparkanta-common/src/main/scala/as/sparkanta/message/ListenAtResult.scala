package as.sparkanta.message

sealed abstract class ListenAtResult(val listenAt: ListenAt, val exception: Option[Exception]) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(listenAt=$listenAt,exception=$exception)"

}

class ListenAtSuccessResult(listenAt: ListenAt) extends ListenAtResult(listenAt, None)

class ListenAtErrorResult(listenAt: ListenAt, exception: Exception) extends ListenAtResult(listenAt, Some(exception))