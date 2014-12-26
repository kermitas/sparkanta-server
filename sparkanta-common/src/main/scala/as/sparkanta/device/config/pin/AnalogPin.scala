package as.sparkanta.device.config.pin

object AnalogPin {
  lazy final val minPinNumber = 0;
  lazy final val maxPinNumber = 7;

  def apply(pinNumber: Int): AnalogPin = pinNumber match {
    case 0 => A0
    case 1 => A1
    case 2 => A2
    case 3 => A3
    case 4 => A4
    case 5 => A5
    case 6 => A6
    case 7 => A7
  }
}

abstract class AnalogPin(pinNumber: Byte) extends Pin(pinNumber) {
  import AnalogPin._
  require(pinNumber >= minPinNumber && pinNumber <= maxPinNumber, s"Wrong analog pin number $pinNumber, should be between $minPinNumber and $maxPinNumber.")

  override def toString = s"${getClass.getSimpleName}(pinNumber=$pinNumber)"
}

object A0 extends AnalogPin(0)
object A1 extends AnalogPin(1)
object A2 extends AnalogPin(2)
object A3 extends AnalogPin(3)
object A4 extends AnalogPin(4)
object A5 extends AnalogPin(5)
object A6 extends AnalogPin(6)
object A7 extends AnalogPin(7)
