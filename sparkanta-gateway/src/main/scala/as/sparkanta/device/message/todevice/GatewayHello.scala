package as.sparkanta.device.message.todevice

object GatewayHello {
  lazy final val messageCode: Int = 5
}

class GatewayHello extends MessageToDevice {

  override def messageCode = GatewayHello.messageCode

  override def toString = s"${getClass.getSimpleName}"

}
