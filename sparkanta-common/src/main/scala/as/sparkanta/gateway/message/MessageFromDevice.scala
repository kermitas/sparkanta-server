package as.sparkanta.gateway.message

import as.sparkanta.device.message.{ MessageFormDevice => MessageFormDeviceMarker }
import java.net.InetSocketAddress

class MessageFromDevice(
  val runtimeId:         Long,
  val sparkDeviceId:     String,
  val softwareVersion:   Int,
  val remoteAddress:     InetSocketAddress,
  val localAddress:      InetSocketAddress,
  val messageFromDevice: MessageFormDeviceMarker
) extends ForwardToRestServer