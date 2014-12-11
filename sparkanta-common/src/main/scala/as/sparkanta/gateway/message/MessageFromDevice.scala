package as.sparkanta.gateway.message

import as.sparkanta.device.message.{ MessageFormDevice => MessageFormDeviceMarker }
import scala.net.IdentifiedInetSocketAddress

class MessageFromDevice(
  val sparkDeviceId:     String,
  val softwareVersion:   Int,
  val remoteAddress:     IdentifiedInetSocketAddress,
  val localAddress:      IdentifiedInetSocketAddress,
  val messageFromDevice: MessageFormDeviceMarker
) extends ForwardToRestServer {

  override def toString = s"${getClass.getSimpleName}(messageFromDevice=$messageFromDevice,sparkDeviceId=$sparkDeviceId,softwareVersion=$softwareVersion,remoteAddress=$remoteAddress,localAddress=$localAddress)"

}