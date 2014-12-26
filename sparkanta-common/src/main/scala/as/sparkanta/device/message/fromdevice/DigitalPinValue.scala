package as.sparkanta.device.message.fromdevice

import as.sparkanta.device.config.pin.{ DigitalPin, DigitalPinValue => DigitalPinValueConfig }
import as.sparkanta.device.{ AckType, NoAck }

object DigitalPinValue {
  lazy final val messageCode: Int = 8
}

class DigitalPinValue(val pin: DigitalPin, val pinValue: DigitalPinValueConfig, val ackType: AckType = NoAck) extends MessageFormDevice {

  override def toString = s"${getClass.getSimpleName}(pin=$pin,pinValue=$pinValue,ackType=$ackType)"

}

