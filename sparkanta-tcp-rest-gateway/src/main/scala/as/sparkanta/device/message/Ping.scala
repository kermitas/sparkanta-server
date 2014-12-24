package as.sparkanta.device.message

import as.sparkanta.device.{ AckType, NoAck }

object Ping {
  lazy final val messageCode: Int = 3
}

class Ping(val ackType: AckType = NoAck) extends MessageToDevice with MessageFormDevice with DoNotForwardToRestServer {

  override def toString = s"${getClass.getSimpleName}(ackType=$ackType)"

}
