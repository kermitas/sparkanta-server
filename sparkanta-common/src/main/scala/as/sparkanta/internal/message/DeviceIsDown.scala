package as.sparkanta.internal.message

class DeviceIsDown(
  val runtimeId:        Long,
  val sparkDeviceId:    String,
  val timeInSystemInMs: Long
) extends ForwardToRestServer