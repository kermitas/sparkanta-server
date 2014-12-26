package as.sparkanta.device.message

import as.sparkanta.device.config.pin.AnalogPin
import as.sparkanta.device.{ AckType, NoAck }

object SetAnalogPinValue {
  lazy final val messageCode: Int = 11
}

class SetAnalogPinValue(val pin: AnalogPin, val pinValue: Char, val ackType: AckType = NoAck) extends MessageToDevice {

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=${pinValue.toInt},ackType=$ackType)"

}
