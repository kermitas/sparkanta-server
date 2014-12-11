package as.sparkanta.gateway.message

import akka.util.ByteString

class DataToDevice(
  val remoteAddressId: Long,
  val data:            ByteString
) extends Serializable {

  def this(remoteAddressId: Long, data: Array[Byte]) = this(remoteAddressId, ByteString(data))

  override def toString = s"${getClass.getSimpleName}(remoteAddressId=$remoteAddressId,data=${data.size} bytes)"
}
