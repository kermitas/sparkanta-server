package as.sparkanta.device.message

import as.sparkanta.device.{ AckType, NoAck }

object GatewayHello {
  lazy final val messageCode: Int = 5
}

class GatewayHello(val ackType: AckType = NoAck) extends MessageFormDevice with MessageToDevice {

  override def toString = s"${getClass.getSimpleName}(ackType=$ackType)"

}
