package as.sparkanta.gateway.message

import java.net.InetSocketAddress

/**
 * Marker trait. It defined that this message should be picked up and send to REST server.
 */
trait ForwardToRestServer extends Serializable {
  def runtimeId: Long
  def sparkDeviceId: String
  def softwareVersion: Int
  def remoteAddress: InetSocketAddress
  def localAddress: InetSocketAddress
}