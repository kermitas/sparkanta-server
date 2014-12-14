package as.sparkanta.gateway.message

import as.sparkanta.gateway.SparkDeviceIdIdentifiedDeviceInfo

class SparkDeviceIdWasIdentified(
  val deviceInfo: SparkDeviceIdIdentifiedDeviceInfo
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(deviceInfo=$deviceInfo)"

}
