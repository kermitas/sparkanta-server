package as.sparkanta.gateway.message

import akka.actor.ActorRef

import scala.net.IdentifiedInetSocketAddress

class SoftwareVersionWasIdentified(
  val softwareVersion: Int,
  val remoteAddress:   IdentifiedInetSocketAddress,
  val localAddress:    IdentifiedInetSocketAddress
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(softwareVersion=$softwareVersion,remoteAddress=$remoteAddress,localAddress=$localAddress)"

}
