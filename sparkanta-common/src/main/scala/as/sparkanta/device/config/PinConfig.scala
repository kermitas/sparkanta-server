package as.sparkanta.device.config

abstract class PinConfig(val pin: Pin) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(pin=$pin)"

}

class DigitalPinConfig(pin: DigitalPin, val workMode: DigitalPinWorkMode) extends PinConfig(pin)
class AnalogPinConfig(pin: AnalogPin, val workMode: AnalogPinWorkMode) extends PinConfig(pin)