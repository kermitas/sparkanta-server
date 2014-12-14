package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress

class SoftwareAndHardwareIdentifiedDeviceInfo(
  remoteAddress:       IdentifiedInetSocketAddress,
  localAddress:        IdentifiedInetSocketAddress,
  startTime:           Long,
  stopTime:            Option[Long],
  val softwareVersion: Int,
  val hardwareVersion: HardwareVersion
) extends NetworkDeviceInfo(remoteAddress, localAddress, startTime, stopTime) {

  def identifySparkDeviceId(sparkDeviceId: String) =
    new SparkDeviceIdIdentifiedDeviceInfo(remoteAddress, localAddress, startTime, stopTime, softwareVersion, hardwareVersion, sparkDeviceId)
}