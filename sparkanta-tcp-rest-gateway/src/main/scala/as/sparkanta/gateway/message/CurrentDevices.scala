package as.sparkanta.gateway.message

class CurrentDevices(val devices: Seq[DeviceRecord]) extends Serializable

case class DeviceRecord(
  runtimeId:       Long,
  remoteIp:        String,
  remotePort:      Int,
  localIp:         String,
  localPort:       Int,
  connectionTime:  Long           = System.currentTimeMillis,
  softwareVersion: Option[Int]    = None,
  sparkDeviceId:   Option[String] = None
) extends Serializable