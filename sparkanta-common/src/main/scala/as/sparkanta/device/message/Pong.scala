package as.sparkanta.device.message

object Pong {
  lazy final val commandCode: Int = 4
}

class Pong extends MessageToDevice with MessageFormDevice
