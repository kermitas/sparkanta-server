package as.sparkanta.device.message.todevice

import as.sparkanta.device.config.pin.{ DigitalPin, DigitalPinValue => DigitalPinValueConfig }
import as.sparkanta.device.{ AckType, NoAck }

object SetDigitalPinValue {
  lazy final val messageCode: Int = 10
}

class SetDigitalPinValue(val pin: DigitalPin, val pinValue: DigitalPinValueConfig, val ackType: AckType = NoAck) extends MessageToDevice {

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=$pinValue,ackType=$ackType)"

}

