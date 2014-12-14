package as.sparkanta.gateway.message

import as.sparkanta.device.message.{ MessageFormDevice => MessageFormDeviceMarker }
import as.sparkanta.gateway.SparkDeviceIdIdentifiedDeviceInfo

class MessageFromDevice(
  val deviceInfo:        SparkDeviceIdIdentifiedDeviceInfo,
  val messageFromDevice: MessageFormDeviceMarker
) extends ForwardToRestServer {

  override def toString = s"${getClass.getSimpleName}(messageFromDevice=$messageFromDevice,deviceInfo=$deviceInfo)"

}