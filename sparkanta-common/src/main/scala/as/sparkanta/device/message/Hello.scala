package as.sparkanta.device.message

object Hello {
  lazy final val commandCode: Int = 1
}

class Hello(val softwareVersion: Int, val sparkDeviceId: String) extends MessageFormDevice /* TODO: just a test, remove soon */ with MessageToDevice
