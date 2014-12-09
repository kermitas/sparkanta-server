package as.sparkanta.device.message

object ServerHello {
  lazy final val commandCode: Int = 5
}

class ServerHello extends MessageFormDevice with MessageToDevice
