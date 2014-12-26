package as.sparkanta.device.message.fromdevice

import as.sparkanta.device.config.pin.AnalogPin
import as.sparkanta.device.{ AckType, NoAck }

object AnalogPinValue {
  lazy final val messageCode: Int = 9
}

class AnalogPinValue(val pin: AnalogPin, val pinValue: Char, val ackType: AckType = NoAck) extends MessageFormDevice {

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=${pinValue.toInt},ackType=$ackType)"

}
