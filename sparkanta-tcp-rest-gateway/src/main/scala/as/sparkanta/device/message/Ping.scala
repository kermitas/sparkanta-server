package as.sparkanta.device.message

object Ping {
  lazy final val messageCode: Int = 3
}

class Ping extends MessageToDevice with MessageFormDevice with DoNotForwardToRestServer {

  override def toString = getClass.getSimpleName

}
