package as.sparkanta.device.message

import as.sparkanta.device.{ AckType, NoAck }

object Disconnect {
  lazy final val messageCode: Int = 2
}

class Disconnect(val delayBeforeNextConnectionAttemptInSeconds: Int, val ackType: AckType = NoAck) extends MessageFormDevice with MessageToDevice {

  require(delayBeforeNextConnectionAttemptInSeconds >= 0 && delayBeforeNextConnectionAttemptInSeconds <= 255, s"Delay before next connection attempt ($delayBeforeNextConnectionAttemptInSeconds) can be only between 0 and 255.")

  override def toString = s"${getClass.getSimpleName}(delayBeforeNextConnectionAttemptInSeconds=$delayBeforeNextConnectionAttemptInSeconds,ackType=$ackType)"

}
