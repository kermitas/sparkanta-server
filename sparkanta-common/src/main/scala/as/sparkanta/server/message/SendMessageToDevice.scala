package as.sparkanta.server.message

import as.sparkanta.device.message.todevice.MessageToDevice

class SendMessageToDevice(
  val remoteAddressId: Long,
  val messageToDevice: MessageToDevice,
  val tcpAck:          Option[Any]     = None
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(remoteAddressId=$remoteAddressId,messageToDevice=$messageToDevice,tcpAck=$tcpAck)"

}