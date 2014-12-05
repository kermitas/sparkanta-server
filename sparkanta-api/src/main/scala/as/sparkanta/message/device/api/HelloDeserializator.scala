package as.sparkanta.message.device.api

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException

class HelloDeserializator extends Deserializator[Hello] {

  protected lazy final val currentDeserializator = new HelloDeserializatorVersion1

  protected lazy final val deserializators = Map[Int, Deserializator[Hello]](
    1 -> currentDeserializator
  )

  override def commandCode: Int = Hello.commandCode

  override def deserialize(is: InputStream): Hello = deserialize(is, is.read)

  protected def deserialize(is: InputStream, version: Int): Hello = deserializators.get(version) match {
    case Some(deserializators) => deserializators.deserialize(is)
    case None                  => throw SerializationVersionNotSupportedException(version)
  }
}

class HelloDeserializatorVersion1 extends Deserializator[Hello] {
  override def commandCode: Int = ???

  override def deserialize(is: InputStream): Hello = {
    val softwareVersion = is.read
    val sparkDeviceIdLength = is.read
    val sparkDeviceIdAsByteArray = new Array[Byte](sparkDeviceIdLength)
    is.read(sparkDeviceIdAsByteArray)
    val sparkDeviceId = new String(sparkDeviceIdAsByteArray)
    new Hello(softwareVersion, sparkDeviceId)
  }
}
