package as.sparkanta.device.message

object Disconnect {
  lazy final val commandCode: Int = 2
}

class Disconnect(val delayBeforeNextConnectionAttemptInSeconds: Int) extends MessageFormDevice with MessageToDevice
