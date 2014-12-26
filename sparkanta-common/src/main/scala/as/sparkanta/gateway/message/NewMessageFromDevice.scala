package as.sparkanta.gateway.message

import as.sparkanta.device.message.fromdevice.MessageFormDevice
import as.sparkanta.gateway.NetworkDeviceInfo

class NewMessageFromDevice(
  val networkDeviceInfo: NetworkDeviceInfo,
  val messageFromDevice: MessageFormDevice
) extends ForwardToRestServer {

  override def toString = s"${getClass.getSimpleName}(messageFromDevice=$messageFromDevice,networkDeviceInfo=$networkDeviceInfo)"

}