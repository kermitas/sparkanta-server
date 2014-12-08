package as.sparkanta.gateway.message

/**
 * Marker trait. It defined that this message should be picked up and send to REST server.
 */
trait ForwardToRestServer extends Serializable {
  def runtimeId: Long
  def sparkDeviceId: String
  def softwareVersion: Int
}