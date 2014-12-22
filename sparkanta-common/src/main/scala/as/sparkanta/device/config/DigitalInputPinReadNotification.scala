package as.sparkanta.device.config

abstract class DigitalInputPinReadNotification(val digitalInputPinReadNotificationType: Byte) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(digitalInputPinReadNotificationType=$digitalInputPinReadNotificationType)"

}

object EachDigitalProbeValue extends DigitalInputPinReadNotification(0)
object EachDigitalProbeChange extends DigitalInputPinReadNotification(1)
/*
object EachDigitalProbeChange extends DigitalInputPinReadNotification(1)
class DigitalProbeChangeFromLowToHigh(val thenContinueToReadHigh: Boolean) extends DigitalInputPinReadNotification(2)
class DigitalProbeChangeFromHighToLow(val thenContinueToReadLow: Boolean) extends DigitalInputPinReadNotification(3)
object EachDigitalProbeHigh extends DigitalInputPinReadNotification(4)
object EachDigitalProbeLow extends DigitalInputPinReadNotification(5)
*/
