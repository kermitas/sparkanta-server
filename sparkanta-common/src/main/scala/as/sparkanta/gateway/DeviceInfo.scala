package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress
//import java.net.InetSocketAddress
import as.sparkanta.device.message.fromdevice.DeviceIdentification

class DeviceInfo(
  remoteAddress:              IdentifiedInetSocketAddress,
  localAddress:               IdentifiedInetSocketAddress,
  restAddress:                IdentifiedInetSocketAddress,
  startTimeInMillis:          Long,
  val deviceIdentification:   DeviceIdentification,
  val pingPongCountPerSecond: Option[Long]                = None
) extends NetworkDeviceInfo(remoteAddress, localAddress, restAddress, startTimeInMillis) {

  def timeInSystemInMillis: Long = System.currentTimeMillis - startTimeInMillis

  override def toString = s"${getClass.getSimpleName}(deviceIdentification=$deviceIdentification,remoteAddress=$remoteAddress,localAddress=$localAddress,restAddress=$restAddress,startTimeInMillis=$startTimeInMillis,timeInSystem=$timeInSystemInMillis,pingPongCountPerSecond=$pingPongCountPerSecond)"
}