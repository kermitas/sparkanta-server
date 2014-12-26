package as.sparkanta.gateway.message

import as.sparkanta.gateway.DeviceInfo

class DeviceIsUp(val deviceInfo: DeviceInfo) extends ForwardToRestServer {

  override def toString = s"${getClass.getSimpleName}(deviceInfo=$deviceInfo)"

}