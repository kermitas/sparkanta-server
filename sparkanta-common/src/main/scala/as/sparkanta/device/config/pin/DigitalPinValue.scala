package as.sparkanta.device.config.pin

object DigitalPinValue {
  def apply(value: Int): DigitalPinValue = if (value > 0) High else Low
}

abstract class DigitalPinValue(val pinValue: Byte) extends Serializable {
  require(pinValue == 0 || pinValue == 1, s"Digital pin value ($pinValue) can be only 0 or 1.")

  override def toString = s"${getClass.getSimpleName}(pinValue=$pinValue)"
}

object Low extends DigitalPinValue(0)
object High extends DigitalPinValue(1)