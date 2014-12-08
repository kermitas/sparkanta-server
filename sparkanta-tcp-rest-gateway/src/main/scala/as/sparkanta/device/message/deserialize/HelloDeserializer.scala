package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.Hello

class HelloDeserializer extends Deserializer[Hello] {

  protected lazy final val currentDeserializer = new HelloDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[Hello]](
    1 -> currentDeserializer
  )

  override def commandCode: Int = Hello.commandCode

  override def deserialize(is: InputStream): Hello = deserialize(is, is.read)

  protected def deserialize(is: InputStream, version: Int): Hello = deserializers.get(version) match {
    case Some(deserializer) => deserializer.deserialize(is)
    case None               => throw SerializationVersionNotSupportedException(version)
  }
}

class HelloDeserializerVersion1 extends Deserializer[Hello] {
  override def commandCode: Int = ???

  override def deserialize(is: InputStream): Hello = {
    val sparkDeviceIdLength = is.read
    val sparkDeviceIdAsByteArray = new Array[Byte](sparkDeviceIdLength)
    is.read(sparkDeviceIdAsByteArray)
    val sparkDeviceId = new String(sparkDeviceIdAsByteArray)
    new Hello(sparkDeviceId)
  }
}
