package scala.net

class IdentifiedConnectionInfo(
  val remote:        IdentifiedInetSocketAddress,
  val local:         IdentifiedInetSocketAddress,
  val startTimeInMs: Long                        = System.currentTimeMillis
) extends Serializable {

  def openedTime = System.currentTimeMillis - startTimeInMs

  override def toString = s"${getClass.getSimpleName}(remote=$remote,local=$local,startTimeInMs=$startTimeInMs)"

}
