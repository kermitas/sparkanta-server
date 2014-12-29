/*
package as.sparkanta.message

import as.sparkanta.gateway.DeviceInfo

class DeviceIsDown(
  val deviceInfo:           DeviceInfo,
  val timeInSystemInMillis: Long
) extends ForwardToRestServer {

  override def restAddressToForwardTo = deviceInfo.restAddress

  override def toString = s"${getClass.getSimpleName}(timeInSystemInMillis=$timeInSystemInMillis,deviceInfo=$deviceInfo)"

}
*/ 