package as.sparkanta.device.config.pin

abstract class AnalogInputPinReadNotification(val analogInputPinReadNotificationType: Byte) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(analogInputPinReadNotificationType=$analogInputPinReadNotificationType)"

}

object EachAnalogProbeValue extends AnalogInputPinReadNotification(0)
object EachAnalogProbeChange extends AnalogInputPinReadNotification(1)
/*
class EachAnalogProbeGreaterThan(val probeValue: Char) extends AnalogInputPinReadNotification(2)
class EachAnalogProbeLessThan(val probeValue: Char) extends AnalogInputPinReadNotification(3)
class EachAnalogProbeInsideOfRange(val startOfRange: Char, val endOfRange: Char) extends AnalogInputPinReadNotification(4)
class EachAnalogProbeOutsideOfRange(val startOfRange: Char, val endOfRange: Char) extends AnalogInputPinReadNotification(5)
*/
