/*
package as.sparkanta.message

import akka.util.ByteString

class SendDataToDevice(
  val remoteAddressId: Long,
  val dataToDevice:    ByteString,
  val ack:             AckType    = NoAck
) extends Serializable {

  def this(
    remoteAddressId: Long,
    dataToDevice:    Array[Byte],
    ack:             AckType
  ) = this(remoteAddressId, ByteString(dataToDevice), ack)

  override def toString = s"${getClass.getSimpleName}(remoteAddressId=$remoteAddressId,dataToDevice=${dataToDevice.length} bytes,ack=$ack)"

}
*/ 