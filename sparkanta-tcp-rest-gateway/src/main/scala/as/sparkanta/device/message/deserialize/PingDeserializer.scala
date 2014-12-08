package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.Ping

class PingDeserializer extends Deserializer[Ping] {

  protected lazy final val currentDeserializer = new PingDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[Ping]](
    1 -> currentDeserializer
  )

  override def commandCode: Int = Ping.commandCode

  override def deserialize(is: InputStream): Ping = deserialize(is, is.read)

  protected def deserialize(is: InputStream, version: Int): Ping = deserializers.get(version) match {
    case Some(deserializer) => deserializer.deserialize(is)
    case None               => throw SerializationVersionNotSupportedException(version)
  }
}

class PingDeserializerVersion1 extends Deserializer[Ping] {
  override def commandCode: Int = ???

  override def deserialize(is: InputStream): Ping = new Ping
}
