package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress

class SparkDeviceIdIdentifiedDeviceInfo(
  remoteAddress:     IdentifiedInetSocketAddress,
  localAddress:      IdentifiedInetSocketAddress,
  startTime:         Long,
  stopTime:          Option[Long],
  softwareVersion:   Int,
  hardwareVersion:   HardwareVersion,
  val sparkDeviceId: String
) extends SoftwareAndHardwareIdentifiedDeviceInfo(remoteAddress, localAddress, startTime, stopTime, softwareVersion, hardwareVersion)