package as.sparkanta.device.message.deserialize

import java.io.InputStream
import as.sparkanta.device.message.MessageFormDevice

class Deserializers(protected final val deserializers: Seq[Deserializer[MessageFormDevice]]) extends Deserializer[MessageFormDevice] {

  def this() = this(
    Seq(
      new DeviceHelloDeserializer,
      new PongDeserializer,
      new DigitalPinDeserializer,
      new AnalogPinDeserializer,
      new AckDeserializer
    )
  )

  override def messageCode: Int = ???

  override def deserialize(is: InputStream, expectedMessageNumber: Int): MessageFormDevice = deserialize(is.read, is, expectedMessageNumber)

  protected def deserialize(messageCode: Int, is: InputStream, expectedMessageNumber: Int): MessageFormDevice =
    deserializers.find(_.messageCode == messageCode).map(_.deserialize(is, expectedMessageNumber)).getOrElse(throw new Exception(s"Unknown message $messageCode code, can not deserialize."))
}
