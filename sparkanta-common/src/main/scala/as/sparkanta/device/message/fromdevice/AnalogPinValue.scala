package as.sparkanta.device.message.fromdevice

import as.sparkanta.device.config.pin.AnalogPin

object AnalogPinValue {
  lazy final val messageCode: Int = 9
}

class AnalogPinValue(val pin: AnalogPin, val pinValue: Char) extends MessageFormDevice {

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=${pinValue.toInt})"

}
