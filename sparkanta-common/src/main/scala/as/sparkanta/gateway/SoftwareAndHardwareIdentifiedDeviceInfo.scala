package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress

class SoftwareAndHardwareIdentifiedDeviceInfo(
  remoteAddress:       IdentifiedInetSocketAddress,
  localAddress:        IdentifiedInetSocketAddress,
  startTime:           Long,
  val softwareVersion: Int,
  val hardwareVersion: HardwareVersion             = Virtual // TODO remove once implemented
) extends NetworkDeviceInfo(remoteAddress, localAddress, startTime)