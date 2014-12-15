package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.ServerHello

class ServerHelloDeserializer extends Deserializer[ServerHello] {

  protected lazy final val currentDeserializer = new ServerHelloDeserializerDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[ServerHello]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = ServerHello.messageCode

  override def deserialize(is: InputStream): ServerHello = deserialize(is, is.read)

  protected def deserialize(is: InputStream, version: Int): ServerHello = deserializers.get(version) match {
    case Some(deserializer) => deserializer.deserialize(is)
    case None               => throw SerializationVersionNotSupportedException(version)
  }
}

class ServerHelloDeserializerDeserializerVersion1 extends Deserializer[ServerHello] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream): ServerHello = new ServerHello
}
