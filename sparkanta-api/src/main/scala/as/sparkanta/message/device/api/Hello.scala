package as.sparkanta.message.device.api

object Hello {
  lazy final val commandCode: Int = 1
}

class Hello(val softwareVersion: Int, val sparkDeviceId: String) extends Serializable with MessageFormDevice
