package as.sparkanta.device.message.fromdevice

import as.sparkanta.device.AckType

object Ack {
  lazy final val messageCode: Int = 12
}

class Ack(val ackedMessageCode: Int, val requestedAckType: AckType) extends MessageFormDevice {

  require(ackedMessageCode >= 0 && ackedMessageCode <= 255, s"Acked message code ($ackedMessageCode) can be only between 0 and 255.")

  override def toString = s"${getClass.getSimpleName}(ackedMessageCode=$ackedMessageCode,requestedAckType=$requestedAckType)"

}