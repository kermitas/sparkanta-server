package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.Ping

class PingDeserializer extends Deserializer[Ping] {

  protected lazy final val currentDeserializer = new PingDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[Ping]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = Ping.messageCode

  override def deserialize(is: InputStream, expectedMessageNumber: Int): Ping = deserialize(is, is.read, expectedMessageNumber)

  protected def deserialize(is: InputStream, serializationVersion: Int, expectedMessageNumber: Int): Ping = deserializers.get(serializationVersion) match {
    case Some(deserializer) => deserializer.deserialize(is, expectedMessageNumber)
    case None               => throw SerializationVersionNotSupportedException(serializationVersion)
  }
}

class PingDeserializerVersion1 extends Deserializer[Ping] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream, expectedMessageNumber: Int): Ping = {

    validateMessageNumber(is.read, expectedMessageNumber)

    new Ping
  }
}
