package as.sparkanta.device.config

object DigitalPin {
  lazy final val minPinNumber = 0;
  lazy final val maxPinNumber = 7;

  def apply(pinNumber: Int): DigitalPin = pinNumber match {
    case 0 => D0
    case 1 => D1
    case 2 => D2
    case 3 => D3
    case 4 => D4
    case 5 => D5
    case 6 => D6
    case 7 => D7
  }
}

abstract class DigitalPin(pinNumber: Byte) extends Pin(pinNumber) {
  import DigitalPin._
  require(pinNumber >= minPinNumber && pinNumber <= maxPinNumber, s"Wrong digital pin number $pinNumber, should be between $minPinNumber and $maxPinNumber.")

  override def toString = s"${getClass.getSimpleName}(pinNumber=$pinNumber)"
}

object D0 extends DigitalPin(0)
object D1 extends DigitalPin(1)
object D2 extends DigitalPin(2)
object D3 extends DigitalPin(3)
object D4 extends DigitalPin(4)
object D5 extends DigitalPin(5)
object D6 extends DigitalPin(6)
object D7 extends DigitalPin(7)