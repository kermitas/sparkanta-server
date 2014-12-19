package as.sparkanta.server.message

import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }

class MessageToDevice(
  val remoteAddressId: Long,
  val messageToDevice: MessageToDeviceMarker,
  val ack:             Option[Any]           = None
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(remoteAddressId=$remoteAddressId,messageToDevice=$messageToDevice,ack=$ack)"

}