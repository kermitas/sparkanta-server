package as.sparkanta.gateway.message

import akka.util.{ ByteString, CompactByteString }

object DataToDevice {
  def concatenate(datas: Seq[Array[Byte]]): ByteString = datas.foldLeft(CompactByteString.empty.asInstanceOf[ByteString])(_ ++ _)
}

class DataToDevice(
  val remoteAddressId: Long,
  val data:            ByteString
) extends Serializable {

  def this(remoteAddressId: Long, datas: Array[Byte]*) = this(remoteAddressId, DataToDevice.concatenate(datas))

  override def toString = s"${getClass.getSimpleName}(remoteAddressId=$remoteAddressId,data=${data.size} bytes)"
}
