package as.sparkanta.gateway.message

import java.net.InetSocketAddress

class CurrentDevices(val devices: Seq[DeviceRecord])

case class DeviceRecord(
  runtimeId:       Long,
  remoteAddress:   InetSocketAddress,
  localAddress:    InetSocketAddress,
  connectionTime:  Long              = System.currentTimeMillis,
  softwareVersion: Option[Int]       = None,
  sparkDeviceId:   Option[String]    = None
)