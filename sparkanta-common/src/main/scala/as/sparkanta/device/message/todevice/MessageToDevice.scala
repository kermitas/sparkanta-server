package as.sparkanta.device.message.todevice

/**
 * Marker trait. It defined that this message should be serialized and send to device.
 */
trait MessageToDevice extends Serializable {
  def messageCode: Int
}