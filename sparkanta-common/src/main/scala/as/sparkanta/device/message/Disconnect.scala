package as.sparkanta.device.message

object Disconnect {
  lazy final val messageCode: Int = 2
}

class Disconnect(val delayBeforeNextConnectionAttemptInSeconds: Int) extends MessageFormDevice with MessageToDevice {

  override def toString = s"${getClass.getSimpleName}(delayBeforeNextConnectionAttemptInSeconds=$delayBeforeNextConnectionAttemptInSeconds)"

}
