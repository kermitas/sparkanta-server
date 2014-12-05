package as.sparkanta.message.device.api

object Hello {
  lazy final val commandCode: Int = 1
}

class Hello(val deviceId: String) extends MessageFormDevice
