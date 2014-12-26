package as.sparkanta.device.message.todevice

import as.sparkanta.device.{ AckType, NoAck }

object Ping {
  lazy final val messageCode: Int = 3
}

class Ping extends MessageToDevice {

  override def toString = s"${getClass.getSimpleName}"

}
