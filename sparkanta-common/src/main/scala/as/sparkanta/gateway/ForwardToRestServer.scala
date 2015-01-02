package as.sparkanta.gateway

import as.sparkanta.device.DeviceInfo

/**
 * Marker trait. It defined that this message should be picked up and send to REST server.
 */
trait ForwardToRestServer extends Serializable {
  def deviceInfo: DeviceInfo
}