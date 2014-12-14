package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress

class NetworkDeviceInfo(
  val remoteAddress: IdentifiedInetSocketAddress,
  val localAddress:  IdentifiedInetSocketAddress,
  val startTime:     Long                        = System.currentTimeMillis,
  var stopTime:      Option[Long]                = None
) extends Serializable {

  def deviceIsDown: Unit = stopTime = Some(System.currentTimeMillis)

  def timeInSystem: Long = stopTime.map(_ - startTime).getOrElse(System.currentTimeMillis - startTime)

  def identifySoftwareAndHardwareVersion(softwareVersion: Int, hardwareVersion: HardwareVersion) =
    new SoftwareAndHardwareIdentifiedDeviceInfo(remoteAddress, localAddress, startTime, stopTime, softwareVersion, hardwareVersion)
}