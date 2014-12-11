package as.sparkanta.gateway.message

import akka.actor.ActorRef
import akka.util.ByteString

import scala.net.IdentifiedInetSocketAddress

class DataFromDevice(
  val data:            ByteString,
  val softwareVersion: Int,
  val remoteAddress:   IdentifiedInetSocketAddress,
  val localAddress:    IdentifiedInetSocketAddress
) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(data=${data.size} bytes,softwareVersion=$softwareVersion,remoteAddress=$remoteAddress,localAddress=$localAddress)"

}