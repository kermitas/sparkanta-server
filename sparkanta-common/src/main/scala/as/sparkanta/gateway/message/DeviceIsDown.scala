package as.sparkanta.gateway.message

import as.sparkanta.gateway.DeviceInfo

class DeviceIsDown(
  val deviceInfo:           DeviceInfo,
  val timeInSystemInMillis: Long
) extends ForwardToRestServer {

  override def toString = s"${getClass.getSimpleName}(deviceInfo=$deviceInfo,timeInSystemInMillis=$timeInSystemInMillis)"

}

/*
import as.sparkanta.gateway.SparkDeviceIdIdentifiedDeviceInfo

class DeviceIsDown(
  val deviceInfo:       SparkDeviceIdIdentifiedDeviceInfo,
  val timeInSystemInMs: Long
) extends ForwardToRestServer {

  override def toString = s"${getClass.getSimpleName}(timeInSystemInMs=$timeInSystemInMs,deviceInfo=$deviceInfo)"

}*/ 