package as.sparkanta.gateway.message

import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }

class MessageToDevice(
  val runtimeId:       Long,
  val messageToDevice: MessageToDeviceMarker
) extends Serializable
