package as.sparkanta.device.message.todevice

object ServerHello {
  lazy final val messageCode: Int = 6
}

class ServerHello extends MessageToDevice {

  override def messageCode = ServerHello.messageCode

  override def toString = s"${getClass.getSimpleName}"

}
