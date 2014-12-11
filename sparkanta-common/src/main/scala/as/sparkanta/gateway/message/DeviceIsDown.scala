package as.sparkanta.gateway.message

import scala.net.IdentifiedInetSocketAddress

class DeviceIsDown(
  val sparkDeviceId:    String,
  val softwareVersion:  Int,
  val remoteAddress:    IdentifiedInetSocketAddress,
  val localAddress:     IdentifiedInetSocketAddress,
  val timeInSystemInMs: Long
) extends ForwardToRestServer {

  override def toString = s"${getClass.getSimpleName}(timeInSystemInMs=$timeInSystemInMs,sparkDeviceId=$sparkDeviceId,softwareVersion=$softwareVersion,remoteAddress=$remoteAddress,localAddress=$localAddress)"

}