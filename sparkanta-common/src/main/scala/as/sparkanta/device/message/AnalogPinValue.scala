package as.sparkanta.device.message

import as.sparkanta.device.config.AnalogPin

object AnalogPinValue {
  lazy final val messageCode: Int = 9
}

class AnalogPinValue(val pin: AnalogPin, val pinValue: Char) extends MessageFormDevice {

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=${pinValue.toInt})"

}
