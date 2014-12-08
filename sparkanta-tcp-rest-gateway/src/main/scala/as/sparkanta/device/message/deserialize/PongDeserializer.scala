package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.Pong

class PongDeserializer extends Deserializer[Pong] {

  protected lazy final val currentDeserializer = new PongDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[Pong]](
    1 -> currentDeserializer
  )

  override def commandCode: Int = Pong.commandCode

  override def deserialize(is: InputStream): Pong = deserialize(is, is.read)

  protected def deserialize(is: InputStream, version: Int): Pong = deserializers.get(version) match {
    case Some(deserializer) => deserializer.deserialize(is)
    case None               => throw SerializationVersionNotSupportedException(version)
  }
}

class PongDeserializerVersion1 extends Deserializer[Pong] {
  override def commandCode: Int = ???

  override def deserialize(is: InputStream): Pong = new Pong
}
