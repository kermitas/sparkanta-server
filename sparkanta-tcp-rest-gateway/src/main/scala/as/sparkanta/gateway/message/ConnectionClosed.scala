package as.sparkanta.gateway.message

import as.sparkanta.gateway.NetworkDeviceInfo

class ConnectionClosed(
  val throwable:          Option[Throwable],
  val closedByRemoteSide: Boolean,
  val deviceInfo:         NetworkDeviceInfo
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(throwable=$throwable,closedByRemoteSide=$closedByRemoteSide,deviceInfo=$deviceInfo)"

}