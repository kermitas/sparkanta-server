package as.sparkanta.gateway.message

class DeviceIsDown(
  val runtimeId:        Long,
  val sparkDeviceId:    String,
  val softwareVersion:  Int,
  val timeInSystemInMs: Long
) extends ForwardToRestServer