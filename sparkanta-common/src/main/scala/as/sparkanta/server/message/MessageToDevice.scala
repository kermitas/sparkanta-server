package as.sparkanta.server.message

import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }

class MessageToDevice(
  val runtimeId:       Long,
  val messageToDevice: MessageToDeviceMarker
) extends Serializable