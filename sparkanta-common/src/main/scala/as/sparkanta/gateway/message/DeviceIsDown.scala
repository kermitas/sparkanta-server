package as.sparkanta.gateway.message

import as.sparkanta.gateway.SparkDeviceIdIdentifiedDeviceInfo

class DeviceIsDown(
  val deviceInfo:       SparkDeviceIdIdentifiedDeviceInfo,
  val timeInSystemInMs: Long
) extends ForwardToRestServer {

  override def toString = s"${getClass.getSimpleName}(timeInSystemInMs=$timeInSystemInMs,deviceInfo=$deviceInfo)"

}