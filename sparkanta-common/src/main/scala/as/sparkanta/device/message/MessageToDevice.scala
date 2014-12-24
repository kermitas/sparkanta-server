package as.sparkanta.device.message

import as.sparkanta.device.AckType

/**
 * Marker trait. It defined that this message should be serialized and send to device.
 */
trait MessageToDevice extends Serializable {

  def ackType: AckType

}