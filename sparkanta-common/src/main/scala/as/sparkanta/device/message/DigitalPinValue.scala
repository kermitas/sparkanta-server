package as.sparkanta.device.message

import as.sparkanta.device.config.{ DigitalPin, DigitalPinValue => DigitalPinValueConfig }

object DigitalPinValue {
  lazy final val messageCode: Int = 8
}

class DigitalPinValue(val pin: DigitalPin, val pinValue: DigitalPinValueConfig) extends MessageFormDevice {

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=$pinValue)"

}

