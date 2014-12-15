package as.sparkanta.device.message

object Pong {
  lazy final val messageCode: Int = 4
}

class Pong extends MessageToDevice with MessageFormDevice with DoNotForwardToRestServer {

  override def toString = getClass.getSimpleName

}
