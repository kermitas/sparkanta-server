package as.sparkanta.device.message

import as.sparkanta.device.config.AnalogPin
import as.sparkanta.device.{ AckType, NoAck }

object AnalogPinValue {
  lazy final val messageCode: Int = 9
}

class AnalogPinValue(val pin: AnalogPin, val pinValue: Char, val ackType: AckType = NoAck) extends MessageFormDevice with MessageToDevice {

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=${pinValue.toInt},ackType=$ackType)"

}
