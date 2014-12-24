package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.Ack
import as.sparkanta.device.AckType

class AckDeserializer extends Deserializer[Ack] {

  protected lazy final val currentDeserializer = new AckDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[Ack]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = Ack.messageCode

  override def deserialize(is: InputStream, expectedMessageNumber: Int): Ack = deserialize(is, is.read, expectedMessageNumber)

  protected def deserialize(is: InputStream, serializationVersion: Int, expectedMessageNumber: Int): Ack = deserializers.get(serializationVersion) match {
    case Some(deserializer) => deserializer.deserialize(is, expectedMessageNumber)
    case None               => throw SerializationVersionNotSupportedException(serializationVersion)
  }
}

class AckDeserializerVersion1 extends Deserializer[Ack] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream, expectedMessageNumber: Int): Ack = {

    validateMessageNumber(is.read, expectedMessageNumber)

    val ackMessageCode = is.read
    val ackType = AckType(is.read)

    new Ack(ackMessageCode, ackType)
  }
}
