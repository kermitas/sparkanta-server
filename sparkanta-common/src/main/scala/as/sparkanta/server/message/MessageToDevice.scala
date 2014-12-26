package as.sparkanta.server.message

import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }

class MessageToDevice(
  val staticId:        Long,
  val messageToDevice: MessageToDeviceMarker,
  val tcpAck:          Option[Any]           = None
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(staticId=$staticId,messageToDevice=$messageToDevice,tcpAck=$tcpAck)"

}