package as.sparkanta.gateway.message

import scala.net.IdentifiedInetSocketAddress

/**
 * Marker trait. It defined that this message should be picked up and send to REST server.
 */
trait ForwardToRestServer extends Serializable {
  def sparkDeviceId: String
  def softwareVersion: Int
  def remoteAddress: IdentifiedInetSocketAddress
  def localAddress: IdentifiedInetSocketAddress
}