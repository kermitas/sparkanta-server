package as.sparkanta.device.config

abstract class AnalogInputPinReadNotification(val analogInputPinReadNotificationType: Byte) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(analogInputPinReadNotificationType=$analogInputPinReadNotificationType)"

}

object EachAnalogProbeValue extends AnalogInputPinReadNotification(0)
/*class EachProbeGreaterThan(val probeValue: Char) extends AnalogInputPinReadNotification(1)
class EachProbeLessThan(val probeValue: Char) extends AnalogInputPinReadNotification(2)
class EachProbeInsideOfRange(val startOfRange: Char, val endOfRange: Char) extends AnalogInputPinReadNotification(3)
class EachProbeOutsideOfRange(val startOfRange: Char, val endOfRange: Char) extends AnalogInputPinReadNotification(4)*/
