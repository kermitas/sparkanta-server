package as.sparkanta.message

class ListeningStopped(
  val listenAt: ListenAt
) extends ForwardToRestServer {

  override def restAddressToForwardTo = listenAt.restAddress

  override def toString = s"${getClass.getSimpleName}(listenAt=$listenAt)"

}
