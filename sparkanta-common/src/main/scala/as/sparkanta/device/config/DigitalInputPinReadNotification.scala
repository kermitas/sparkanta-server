package as.sparkanta.device.config

abstract class DigitalInputPinReadNotification(val digitalInputPinReadNotificationType: Byte) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(digitalInputPinReadNotificationType=$digitalInputPinReadNotificationType)"

}

object EachDigitalProbeValue extends DigitalInputPinReadNotification(0)
/*class FromLowToHigh(val thenContinueToReadHigh: Boolean) extends DigitalInputPinReadNotification(1)
class FromHighToLow(val thenContinueToReadLow: Boolean) extends DigitalInputPinReadNotification(2)
object EachHigh extends DigitalInputPinReadNotification(3)
object EachLow extends DigitalInputPinReadNotification(4)*/
