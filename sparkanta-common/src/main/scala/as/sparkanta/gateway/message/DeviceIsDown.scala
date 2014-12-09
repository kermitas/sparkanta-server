package as.sparkanta.gateway.message

class DeviceIsDown(
  val runtimeId:        Long,
  val sparkDeviceId:    String,
  val softwareVersion:  Int,
  val remoteIp:         String,
  val remotePort:       Int,
  val localIp:          String,
  val localPort:        Int,
  val timeInSystemInMs: Long
) extends ForwardToRestServer