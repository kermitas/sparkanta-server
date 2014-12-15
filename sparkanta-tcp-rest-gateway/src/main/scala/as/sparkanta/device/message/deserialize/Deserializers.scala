package as.sparkanta.device.message.deserialize

import java.io.InputStream
import as.sparkanta.device.message.MessageFormDevice

class Deserializers(protected final val deserializers: Seq[Deserializer[MessageFormDevice]]) extends Deserializer[MessageFormDevice] {

  def this() = this(
    Seq(
      new DeviceHelloDeserializer,
      new DisconnectDeserializer,
      new PingDeserializer,
      new PongDeserializer,
      new GatewayHelloDeserializer,
      new ServerHelloDeserializer
    )
  )

  override def messageCode: Int = ???

  override def deserialize(is: InputStream): MessageFormDevice = deserialize(is, is.read)

  protected def deserialize(is: InputStream, messageCode: Int): MessageFormDevice =
    deserializers.find(_.messageCode == messageCode).map(_.deserialize(is)).getOrElse(throw new Exception(s"Unknown message $messageCode code, can not deserialize."))

}
