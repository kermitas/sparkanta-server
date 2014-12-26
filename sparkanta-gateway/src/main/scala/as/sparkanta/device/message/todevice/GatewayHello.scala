package as.sparkanta.device.message.todevice

import as.sparkanta.device.{ AckType, NoAck }

object GatewayHello {
  lazy final val messageCode: Int = 5
}

class GatewayHello extends MessageToDevice {

  override def toString = s"${getClass.getSimpleName}"

}
