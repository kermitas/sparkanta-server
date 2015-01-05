package as.sparkanta.device.message.todevice

object Ping {
  lazy final val messageCode: Int = 3
}

class Ping extends MessageToDevice {

  override def messageCode = Ping.messageCode

  override def toString = s"${getClass.getSimpleName}"

}
