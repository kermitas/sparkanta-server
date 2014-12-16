package as.sparkanta.device.message

object PinConfiguration {
  lazy final val messageCode: Int = 7
}

class PinConfiguration(
  val a0: AnalogPinConfig,
  val a1: AnalogPinConfig,
  val a2: AnalogPinConfig,
  val a3: AnalogPinConfig,
  val a4: AnalogPinConfig,
  val a5: AnalogPinConfig,
  val a6: AnalogPinConfig,
  val a7: AnalogPinConfig,
  val d0: DigitalPinConfig,
  val d1: DigitalPinConfig,
  val d2: DigitalPinConfig,
  val d3: DigitalPinConfig,
  val d4: DigitalPinConfig,
  val d5: DigitalPinConfig,
  val d6: DigitalPinConfig,
  val d7: DigitalPinConfig

) extends MessageToDevice {

  override def toString = getClass.getSimpleName

}

// ---

abstract class Pin(val pinNumber: Byte) extends Serializable

// ---

object DigitalPin {
  lazy final val minPinNumber = 0;
  lazy final val maxPinNumber = 7;
}

abstract class DigitalPin(pinNumber: Byte) extends Pin(pinNumber) {
  import DigitalPin._
  require(pinNumber >= minPinNumber && pinNumber <= maxPinNumber, s"Wrong digital pin number $pinNumber, should be between $minPinNumber and $maxPinNumber.")
}

object D0 extends DigitalPin(0)
object D1 extends DigitalPin(1)
object D2 extends DigitalPin(2)
object D3 extends DigitalPin(3)
object D4 extends DigitalPin(4)
object D5 extends DigitalPin(5)
object D6 extends DigitalPin(6)
object D7 extends DigitalPin(7)

// ---

object AnalogPin {
  lazy final val minPinNumber = 0;
  lazy final val maxPinNumber = 7;
}

abstract class AnalogPin(pinNumber: Byte) extends Pin(pinNumber) {
  import AnalogPin._
  require(pinNumber >= minPinNumber && pinNumber <= maxPinNumber, s"Wrong analog pin number $pinNumber, should be between $minPinNumber and $maxPinNumber.")
}

object A0 extends AnalogPin(0)
object A1 extends AnalogPin(1)
object A2 extends AnalogPin(2)
object A3 extends AnalogPin(3)
object A4 extends AnalogPin(4)
object A5 extends AnalogPin(5)
object A6 extends AnalogPin(6)
object A7 extends AnalogPin(7)

// ---

abstract class PinConfig(val pin: Pin) extends Serializable

class DigitalPinConfig(pin: DigitalPin, val workMode: DigitalPinWorkMode) extends PinConfig(pin)
class AnalogPinConfig(pin: AnalogPin, val workMode: AnalogPinWorkMode) extends PinConfig(pin)

// ---

abstract class DigitalPinValue(val pinValue: Byte) extends Serializable { require(pinValue == 0 || pinValue == 1, s"Digital pin value ($pinValue) can be only 0 or 1.") }

object Low extends DigitalPinValue(0)
object High extends DigitalPinValue(1)

// ---

abstract class DigitalInputPinReadNotification(val digitalInputPinReadNotificationType: Byte) extends Serializable

object EachDigitalProbeValue extends DigitalInputPinReadNotification(0)
/*class FromLowToHigh(val thenContinueToReadHigh: Boolean) extends DigitalInputPinReadNotification(1)
class FromHighToLow(val thenContinueToReadLow: Boolean) extends DigitalInputPinReadNotification(2)
object EachHigh extends DigitalInputPinReadNotification(3)
object EachLow extends DigitalInputPinReadNotification(4)*/

// ---

abstract class DigitalPinWorkMode(val workMode: Byte) extends Serializable

class DigitalOutput(val initialPinValue: DigitalPinValue) extends DigitalPinWorkMode(0)
class DigitalInput(val probeTimeInMs: Char, val readNotification: DigitalInputPinReadNotification) extends DigitalPinWorkMode(1)
class DigitalPullUpInput(val probeTimeInMs: Char, val readNotification: DigitalInputPinReadNotification) extends DigitalPinWorkMode(2)
class DigitalPullDownInput(val probeTimeInMs: Char, val readNotification: DigitalInputPinReadNotification) extends DigitalPinWorkMode(3)

// ---

abstract class AnalogInputPinReadNotification(val analogInputPinReadNotificationType: Byte) extends Serializable

object EachAnalogProbeValue extends AnalogInputPinReadNotification(0)
/*class EachProbeGreaterThan(val probeValue: Char) extends AnalogInputPinReadNotification(1)
class EachProbeLessThan(val probeValue: Char) extends AnalogInputPinReadNotification(2)
class EachProbeInsideOfRange(val startOfRange: Char, val endOfRange: Char) extends AnalogInputPinReadNotification(3)
class EachProbeOutsideOfRange(val startOfRange: Char, val endOfRange: Char) extends AnalogInputPinReadNotification(4)*/

// ---

abstract class AnalogPinWorkMode(val workMode: Byte) extends Serializable

class AnalogOutput(val initialPinValue: Char) extends AnalogPinWorkMode(0) { require(initialPinValue >= 0 && initialPinValue <= 255, s"Initial analog pin value ($initialPinValue) can be only between 0 and 255.") }
class AnalogInput(val probeTimeInMs: Char, val readNotification: AnalogInputPinReadNotification) extends AnalogPinWorkMode(1)
class AnalogPullUpInput(val probeTimeInMs: Char, val readNotification: AnalogInputPinReadNotification) extends AnalogPinWorkMode(2)
class AnalogPullDownInput(val probeTimeInMs: Char, val readNotification: AnalogInputPinReadNotification) extends AnalogPinWorkMode(3)

// ---