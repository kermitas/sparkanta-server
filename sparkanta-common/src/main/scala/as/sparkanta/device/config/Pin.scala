package as.sparkanta.device.config

abstract class Pin(val pinNumber: Byte) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(pinNumber=$pinNumber)"

}