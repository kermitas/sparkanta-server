package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress
import java.net.InetSocketAddress

class DeviceInfo(
  val staticId:               Long,
  val softwareVersion:        Int,
  val hardwareVersion:        HardwareVersion,
  val deviceUniqueName:       String,
  val remoteAddress:          InetSocketAddress,
  val localAddress:           IdentifiedInetSocketAddress,
  val startTimeInMillis:      Long                        = System.currentTimeMillis,
  val pingPongCountPerSecond: Option[Long]                = None
) extends Serializable {

  def timeInSystemInMillis: Long = System.currentTimeMillis - startTimeInMillis

  override def toString = s"${getClass.getSimpleName}(staticId=$staticId,softwareVersion=$softwareVersion,hardwareVersion=$hardwareVersion,deviceUniqueName=$deviceUniqueName,remoteAddress=$remoteAddress,localAddress=$localAddress,startTimeInMillis=$startTimeInMillis,pingPongCountPerSecond=$pingPongCountPerSecond)"
}