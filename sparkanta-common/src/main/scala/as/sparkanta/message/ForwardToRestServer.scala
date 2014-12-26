package as.sparkanta.message

import scala.net.IdentifiedInetSocketAddress

/**
 * Marker trait. It defined that this message should be picked up and send to REST server.
 */
trait ForwardToRestServer extends Serializable {
  def restAddressToForwardTo: IdentifiedInetSocketAddress
}