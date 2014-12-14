package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress

class SparkDeviceIdIdentifiedDeviceInfo(
  remoteAddress:     IdentifiedInetSocketAddress,
  localAddress:      IdentifiedInetSocketAddress,
  startTime:         Long,
  softwareVersion:   Int,
  hardwareVersion:   HardwareVersion             = Virtual, // TODO remove once implemented
  val sparkDeviceId: String
) extends SoftwareAndHardwareIdentifiedDeviceInfo(remoteAddress, localAddress, startTime, softwareVersion, hardwareVersion)