package as.sparkanta.device.message.fromdevice

import as.sparkanta.device.AckType

object Ack {
  lazy final val messageCode: Int = 12
}

class Ack(val ackMessageCode: Int, val ackType: AckType) extends MessageFormDevice {

  require(ackMessageCode >= 0 && ackMessageCode <= 255, s"Ack message code ($ackMessageCode) can be only between 0 and 255.")

  override def toString = s"${getClass.getSimpleName}(ackMessageCode=$ackMessageCode,ackType=$ackType)"

}