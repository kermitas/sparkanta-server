package as.sparkanta.internal.message

import as.sparkanta.device.message.{ MessageFormDevice => MessageFormDeviceMarker }

class MessageFromDevice(
  val runtimeId:         Long,
  val messageFromDevice: MessageFormDeviceMarker
) extends Serializable