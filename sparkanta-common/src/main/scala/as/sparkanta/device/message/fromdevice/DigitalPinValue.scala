package as.sparkanta.device.message.fromdevice

import as.sparkanta.device.config.pin.{ DigitalPin, DigitalPinValue => DigitalPinValueConfig }

object DigitalPinValue {
  lazy final val messageCode: Int = 8
}

class DigitalPinValue(val pin: DigitalPin, val pinValue: DigitalPinValueConfig) extends MessageFromDevice {

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=$pinValue)"

}

