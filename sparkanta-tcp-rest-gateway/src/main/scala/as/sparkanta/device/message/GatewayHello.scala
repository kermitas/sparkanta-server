package as.sparkanta.device.message

object GatewayHello {
  lazy final val messageCode: Int = 5
}

class GatewayHello extends MessageFormDevice with MessageToDevice {

  override def toString = getClass.getSimpleName

}
