package as.sparkanta.gateway.message

class DeviceIsDown(
  val runtimeId:        Long,
  val sparkDeviceId:    String,
  val timeInSystemInMs: Long
) extends Serializable