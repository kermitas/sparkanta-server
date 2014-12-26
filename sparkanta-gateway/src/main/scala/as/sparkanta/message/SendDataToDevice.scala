package as.sparkanta.message

import akka.util.ByteString

class SendDataToDevice(
  val remoteAddressId: Long,
  val dataToDevice:    ByteString,
  val ack:             AckType    = NoAck
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(remoteAddressId=$remoteAddressId,dataToDevice=${dataToDevice.length} bytes,ack=$ack)"

}