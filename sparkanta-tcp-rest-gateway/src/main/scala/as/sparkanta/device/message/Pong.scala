package as.sparkanta.device.message

import as.sparkanta.device.{ AckType, NoAck }

object Pong {
  lazy final val messageCode: Int = 4
}

class Pong(val ackType: AckType = NoAck) extends MessageToDevice with MessageFormDevice with DoNotForwardToRestServer {

  override def toString = s"${getClass.getSimpleName}(ackType=$ackType)"

}
