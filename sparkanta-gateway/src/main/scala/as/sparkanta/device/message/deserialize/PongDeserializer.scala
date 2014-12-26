package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.fromdevice.Pong

class PongDeserializer extends Deserializer[Pong] {

  protected lazy final val currentDeserializer = new PongDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[Pong]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = Pong.messageCode

  override def deserialize(is: InputStream, expectedMessageNumber: Int): Pong = deserialize(is, is.read, expectedMessageNumber)

  protected def deserialize(is: InputStream, serializationVersion: Int, expectedMessageNumber: Int): Pong = deserializers.get(serializationVersion) match {
    case Some(deserializer) => deserializer.deserialize(is, expectedMessageNumber)
    case None               => throw SerializationVersionNotSupportedException(serializationVersion)
  }
}

class PongDeserializerVersion1 extends Deserializer[Pong] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream, expectedMessageNumber: Int): Pong = {

    validateMessageNumber(is.read, expectedMessageNumber)

    new Pong
  }
}
