package as.sparkanta.device.config

abstract class DigitalPinWorkMode(val workMode: Byte) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(workMode=$workMode)"

}

class DigitalOutput(val initialPinValue: DigitalPinValue) extends DigitalPinWorkMode(0)
class DigitalInput(val probeTimeInMs: Char, val readNotification: DigitalInputPinReadNotification) extends DigitalPinWorkMode(1)
class DigitalPullUpInput(val probeTimeInMs: Char, val readNotification: DigitalInputPinReadNotification) extends DigitalPinWorkMode(2)
class DigitalPullDownInput(val probeTimeInMs: Char, val readNotification: DigitalInputPinReadNotification) extends DigitalPinWorkMode(3)
