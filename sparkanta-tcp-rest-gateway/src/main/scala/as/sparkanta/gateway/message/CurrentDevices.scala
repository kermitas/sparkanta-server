package as.sparkanta.gateway.message

import scala.net.IdentifiedInetSocketAddress

class CurrentDevices(val devices: Seq[DeviceRecord]) extends Serializable

case class DeviceRecord(
  remoteAddress:   IdentifiedInetSocketAddress,
  localAddress:    IdentifiedInetSocketAddress,
  connectionTime:  Long                        = System.currentTimeMillis,
  softwareVersion: Option[Int]                 = None,
  sparkDeviceId:   Option[String]              = None
) extends Serializable