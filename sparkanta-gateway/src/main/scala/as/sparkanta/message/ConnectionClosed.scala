package as.sparkanta.message

import as.sparkanta.gateway.NetworkDeviceInfo

class ConnectionClosed(val networkDeviceInfo: NetworkDeviceInfo, val exception: Option[Exception] = None) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(networkDeviceInfo=$networkDeviceInfo,exception=$exception)"

}
