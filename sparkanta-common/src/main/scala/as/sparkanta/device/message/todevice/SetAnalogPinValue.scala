package as.sparkanta.device.message.todevice

import as.sparkanta.device.config.pin.AnalogPin

object SetAnalogPinValue {
  lazy final val messageCode: Int = 11
}

class SetAnalogPinValue(val pin: AnalogPin, val pinValue: Int) extends MessageToDevice {

  require(pinValue >= 0 && pinValue <= 255, s"Pin value ($pinValue) can be only between 0 and 255.")

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=${pinValue.toInt})"

}
