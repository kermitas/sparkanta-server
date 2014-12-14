package as.sparkanta.gateway

import scala.net.IdentifiedInetSocketAddress

class NetworkDeviceInfo(
  val remoteAddress: IdentifiedInetSocketAddress,
  val localAddress:  IdentifiedInetSocketAddress,
  val startTime:     Long                        = System.currentTimeMillis,
  var stopTime:      Option[Long]                = None
) extends Serializable {

  protected var precalculatedTimeInSystem: Option[Long] = None

  def deviceIsDown: Unit = {
    stopTime = Some(System.currentTimeMillis)
    precalculatedTimeInSystem = Some(stopTime.get - startTime)
  }

  def timeInSystem: Long = precalculatedTimeInSystem.getOrElse(System.currentTimeMillis - startTime)
}