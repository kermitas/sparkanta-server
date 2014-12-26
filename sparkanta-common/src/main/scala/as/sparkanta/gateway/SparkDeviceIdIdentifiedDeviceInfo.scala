/*
package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress

class SparkDeviceIdIdentifiedDeviceInfo(
  remoteAddress:              IdentifiedInetSocketAddress,
  localAddress:               IdentifiedInetSocketAddress,
  startTime:                  Long,
  softwareVersion:            Int,
  hardwareVersion:            HardwareVersion,
  val sparkDeviceId:          String,
  val pingPongCountPerSecond: Option[Long]
) extends SoftwareAndHardwareIdentifiedDeviceInfo(remoteAddress, localAddress, startTime, softwareVersion, hardwareVersion) {

  override def toString = s"${getClass.getSimpleName}(remoteAddress=$remoteAddress,localAddress=$localAddress,startTime=$startTime,softwareVersion=$softwareVersion,hardwareVersion=$hardwareVersion,sparkDeviceId=$sparkDeviceId,pingPongCountPerSecond=$pingPongCountPerSecond)"

}
*/ 