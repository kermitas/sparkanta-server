package as.sparkanta.device.config.pin

abstract class AnalogPinWorkMode(val workMode: Byte) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(workMode=$workMode)"

}

class AnalogOutput(val initialPinValue: Char) extends AnalogPinWorkMode(0) { require(initialPinValue >= 0 && initialPinValue <= 255, s"Initial analog pin value ($initialPinValue) can be only between 0 and 255.") }
class AnalogInput(val probeTimeInMs: Char, val readNotification: AnalogInputPinReadNotification) extends AnalogPinWorkMode(1)
class AnalogPullUpInput(val probeTimeInMs: Char, val readNotification: AnalogInputPinReadNotification) extends AnalogPinWorkMode(2)
class AnalogPullDownInput(val probeTimeInMs: Char, val readNotification: AnalogInputPinReadNotification) extends AnalogPinWorkMode(3)
