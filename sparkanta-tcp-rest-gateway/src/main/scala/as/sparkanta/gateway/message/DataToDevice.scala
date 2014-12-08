package as.sparkanta.gateway.message

import akka.util.ByteString

class DataToDevice(
  val runtimeId: Long,
  val data:      ByteString
) extends Serializable {

  def this(runtimeId: Long, data: Array[Byte]) = this(runtimeId, ByteString(data))

}
