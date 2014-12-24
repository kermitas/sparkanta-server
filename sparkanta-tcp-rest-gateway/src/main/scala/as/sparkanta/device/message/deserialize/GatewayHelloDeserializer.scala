package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.GatewayHello

class GatewayHelloDeserializer extends Deserializer[GatewayHello] {

  protected lazy final val currentDeserializer = new GatewayHelloDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[GatewayHello]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = GatewayHello.messageCode

  override def deserialize(is: InputStream, expectedMessageNumber: Int): GatewayHello = deserialize(is, is.read, expectedMessageNumber)

  protected def deserialize(is: InputStream, serializationVersion: Int, expectedMessageNumber: Int): GatewayHello = deserializers.get(serializationVersion) match {
    case Some(deserializer) => deserializer.deserialize(is, expectedMessageNumber)
    case None               => throw SerializationVersionNotSupportedException(serializationVersion)
  }
}

class GatewayHelloDeserializerVersion1 extends Deserializer[GatewayHello] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream, expectedMessageNumber: Int): GatewayHello = {

    validateMessageNumber(is.read, expectedMessageNumber)

    new GatewayHello
  }
}
