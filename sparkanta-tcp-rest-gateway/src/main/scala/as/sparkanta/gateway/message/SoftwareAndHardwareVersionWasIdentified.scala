package as.sparkanta.gateway.message

import scala.net.IdentifiedInetSocketAddress
import as.sparkanta.gateway.HardwareVersion

class SoftwareAndHardwareVersionWasIdentified(
  val softwareVersion: Int,
  val hardwareVersion: HardwareVersion,
  val remoteAddress:   IdentifiedInetSocketAddress,
  val localAddress:    IdentifiedInetSocketAddress
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(softwareVersion=$softwareVersion,hardwareVersion=$hardwareVersion,remoteAddress=$remoteAddress,localAddress=$localAddress)"

}
