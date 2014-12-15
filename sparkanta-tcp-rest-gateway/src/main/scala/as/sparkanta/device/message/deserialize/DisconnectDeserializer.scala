package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.Disconnect

class DisconnectDeserializer extends Deserializer[Disconnect] {

  protected lazy final val currentDeserializer = new DisconnectDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[Disconnect]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = Disconnect.messageCode

  override def deserialize(is: InputStream): Disconnect = deserialize(is, is.read)

  protected def deserialize(is: InputStream, version: Int): Disconnect = deserializers.get(version) match {
    case Some(deserializer) => deserializer.deserialize(is)
    case None               => throw SerializationVersionNotSupportedException(version)
  }
}

class DisconnectDeserializerVersion1 extends Deserializer[Disconnect] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream): Disconnect = {
    val delayBeforeNextConnectionAttemptInSeconds = is.read
    new Disconnect(delayBeforeNextConnectionAttemptInSeconds)
  }
}
