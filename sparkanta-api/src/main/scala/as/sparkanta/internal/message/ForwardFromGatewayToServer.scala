package as.sparkanta.internal.message

/**
 * Marker trait. It defined that this message should be forwarded from tcp-rest-gateway to rest-server.
 */
trait ForwardFromGatewayToServer {
  def sparkDeviceId: String
  def runtimeId: Long
}
