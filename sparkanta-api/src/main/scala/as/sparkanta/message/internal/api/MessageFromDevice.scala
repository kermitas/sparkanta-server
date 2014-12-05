package as.sparkanta.message.internal.api

import as.sparkanta.message.device.api.{ MessageFormDevice => MessageFormDeviceSpec }

class MessageFromDevice(val runtimeId: Long, val sparkDeviceId: String, val messageFromDevice: MessageFormDeviceSpec) extends Serializable with ForwardFromGatewayToServer