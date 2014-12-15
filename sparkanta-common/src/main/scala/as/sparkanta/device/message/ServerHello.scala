package as.sparkanta.device.message

object ServerHello {
  lazy final val messageCode: Int = 6
}

class ServerHello extends MessageFormDevice with MessageToDevice {

  override def toString = getClass.getSimpleName

}
