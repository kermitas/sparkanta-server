package as.sparkanta.gateway.message

import akka.actor.ActorRef
import scala.net.IdentifiedInetSocketAddress

class SparkDeviceIdWasIdentified(
  val sparkDeviceId:   String,
  val softwareVersion: Int,
  val remoteAddress:   IdentifiedInetSocketAddress,
  val localAddress:    IdentifiedInetSocketAddress
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(sparkDeviceId=$sparkDeviceId,softwareVersion=$softwareVersion,remoteAddress=$remoteAddress,localAddress=$localAddress)"

}
