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

  override def deserialize(is: InputStream, expectedMessageNumber: Int): ServerHello = deserialize(is, is.read, expectedMessageNumber)

  protected def deserialize(is: InputStream, serializationVersion: Int, expectedMessageNumber: Int): ServerHello = deserializers.get(serializationVersion) match {
    case Some(deserializer) => deserializer.deserialize(is, expectedMessageNumber)
    case None               => throw SerializationVersionNotSupportedException(serializationVersion)
  }
}

class ServerHelloDeserializerDeserializerVersion1 extends Deserializer[ServerHello] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream, expectedMessageNumber: Int): ServerHello = {

    validateMessageNumber(is.read, expectedMessageNumber)

    new ServerHello
  }
}
