package as.sparkanta.device.message.deserialize

import java.io.InputStream
import as.sparkanta.device.message.MessageFormDevice

class Deserializers(protected final val deserializers: Seq[Deserializer[MessageFormDevice]]) extends Deserializer[MessageFormDevice] {

  def this() = this(
    Seq(
      new DeviceHelloDeserializer,
      new PingDeserializer,
      new PongDeserializer,
      new ServerHelloDeserializer
    )
  )

  override def commandCode: Int = ???

  override def deserialize(is: InputStream): MessageFormDevice = deserialize(is, is.read)

  protected def deserialize(is: InputStream, commandCode: Int): MessageFormDevice =
    deserializers.find(_.commandCode == commandCode).map(_.deserialize(is)).getOrElse(throw new Exception(s"Unknown command $commandCode code, can not deserialize."))

}
