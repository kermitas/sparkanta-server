package as.sparkanta.device.message

import as.sparkanta.device.{ AckType, NoAck }

object ServerHello {
  lazy final val messageCode: Int = 6
}

class ServerHello(val ackType: AckType = NoAck) extends MessageFormDevice with MessageToDevice {

  override def toString = s"${getClass.getSimpleName}(ackType=$ackType)"

}
