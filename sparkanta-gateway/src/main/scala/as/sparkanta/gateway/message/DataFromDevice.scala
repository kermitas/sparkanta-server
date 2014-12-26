/*
package as.sparkanta.gateway.message

import akka.util.ByteString
import as.sparkanta.gateway.NetworkDeviceInfo

class DataFromDevice(
  val data:       ByteString,
  val deviceInfo: NetworkDeviceInfo
) extends Serializable {

  //override def toString = s"${getClass.getSimpleName}(data=${data.size} bytes (${data.map("" + _).mkString(",")}),deviceInfo=$deviceInfo)"
  override def toString = s"${getClass.getSimpleName}(data=${data.size} bytes,deviceInfo=$deviceInfo)"

}
*/ 