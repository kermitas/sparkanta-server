package as.sparkanta.message

import as.sparkanta.gateway.NetworkDeviceInfo

class ConnectionClosed(val networkDeviceInfo: NetworkDeviceInfo) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(networkDeviceInfo=$networkDeviceInfo)"

}
