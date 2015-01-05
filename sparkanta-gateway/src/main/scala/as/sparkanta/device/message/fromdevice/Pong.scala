package as.sparkanta.device.message.fromdevice

object Pong {
  lazy final val messageCode: Int = 4
}

class Pong extends MessageFromDeviceThatShouldNotBeForwardedToRestServer {

  override def toString = s"${getClass.getSimpleName}"

}
