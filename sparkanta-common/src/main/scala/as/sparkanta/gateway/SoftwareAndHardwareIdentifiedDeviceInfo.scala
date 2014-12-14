package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress

class SoftwareAndHardwareIdentifiedDeviceInfo(
  remoteAddress:       IdentifiedInetSocketAddress,
  localAddress:        IdentifiedInetSocketAddress,
  startTime:           Long,
  stopTime:            Option[Long],
  val softwareVersion: Int,
  val hardwareVersion: HardwareVersion             = Virtual
) extends NetworkDeviceInfo(remoteAddress, localAddress, startTime, stopTime)