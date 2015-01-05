package as.sparkanta.device.message.todevice

import as.sparkanta.device.config.pin.{ DigitalPin, DigitalPinValue }

object SetDigitalPinValue {
  lazy final val messageCode: Int = 10
}

class SetDigitalPinValue(val pin: DigitalPin, val pinValue: DigitalPinValue) extends MessageToDevice {

  override def messageCode = SetDigitalPinValue.messageCode

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=$pinValue)"

}

