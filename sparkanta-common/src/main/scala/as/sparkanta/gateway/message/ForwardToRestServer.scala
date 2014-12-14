package as.sparkanta.gateway.message

import as.sparkanta.gateway.SparkDeviceIdIdentifiedDeviceInfo

/**
 * Marker trait. It defined that this message should be picked up and send to REST server.
 */
trait ForwardToRestServer extends Serializable {
  def deviceInfo: SparkDeviceIdIdentifiedDeviceInfo
}