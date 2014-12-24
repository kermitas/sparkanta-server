package as.sparkanta.device.message

import as.sparkanta.device.config.{ DigitalPin, DigitalPinValue => DigitalPinValueConfig }
import as.sparkanta.device.{ AckType, NoAck }

object DigitalPinValue {
  lazy final val messageCode: Int = 8
}

class DigitalPinValue(val pin: DigitalPin, val pinValue: DigitalPinValueConfig, val ackType: AckType = NoAck) extends MessageFormDevice with MessageToDevice {

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=$pinValue,ackType=$ackType)"

}

