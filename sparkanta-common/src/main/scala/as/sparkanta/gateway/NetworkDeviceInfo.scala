/*
package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress

class NetworkDeviceInfo(
  val remoteAddress: IdentifiedInetSocketAddress,
  val localAddress:  IdentifiedInetSocketAddress,
  val startTime:     Long                        = System.currentTimeMillis
) extends Serializable {

  def timeInSystem: Long = System.currentTimeMillis - startTime

  def identifySoftwareAndHardwareVersion(softwareVersion: Int, hardwareVersion: HardwareVersion) =
    new SoftwareAndHardwareIdentifiedDeviceInfo(remoteAddress, localAddress, startTime, softwareVersion, hardwareVersion)

  override def toString = s"${getClass.getSimpleName}(remoteAddress=$remoteAddress,localAddress=$localAddress,startTime=$startTime)"
}
*/ 