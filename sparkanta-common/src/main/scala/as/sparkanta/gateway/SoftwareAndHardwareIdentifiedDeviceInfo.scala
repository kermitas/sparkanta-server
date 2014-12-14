package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress

class SoftwareAndHardwareIdentifiedDeviceInfo(
  remoteAddress:       IdentifiedInetSocketAddress,
  localAddress:        IdentifiedInetSocketAddress,
  startTime:           Long,
  val softwareVersion: Int,
  val hardwareVersion: HardwareVersion
) extends NetworkDeviceInfo(remoteAddress, localAddress, startTime) {

  def identifySparkDeviceId(sparkDeviceId: String) =
    new SparkDeviceIdIdentifiedDeviceInfo(remoteAddress, localAddress, startTime, softwareVersion, hardwareVersion, sparkDeviceId)

  override def toString = s"${getClass.getSimpleName}(remoteAddress=$remoteAddress,localAddress=$localAddress,startTime=$startTime,softwareVersion=$softwareVersion,hardwareVersion=$hardwareVersion)"
}