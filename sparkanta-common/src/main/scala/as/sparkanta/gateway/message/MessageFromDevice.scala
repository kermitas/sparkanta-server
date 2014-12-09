package as.sparkanta.gateway.message

import as.sparkanta.device.message.{ MessageFormDevice => MessageFormDeviceMarker }

class MessageFromDevice(
  val runtimeId:         Long,
  val sparkDeviceId:     String,
  val softwareVersion:   Int,
  val remoteIp:          String,
  val remotePort:        Int,
  val localIp:           String,
  val localPort:         Int,
  val messageFromDevice: MessageFormDeviceMarker
) extends ForwardToRestServer