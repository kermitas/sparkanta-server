package as.sparkanta.message.internal.api

/**
 * Marker trait. It defined that this message should be forwarded from tcp-rest-gateway to rest-server.
 */
trait ForwardFromGatewayToServer {
  def sparkDeviceId: String
  def runtimeId: Long
}
