package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.DeviceHello

class DeviceHelloDeserializer extends Deserializer[DeviceHello] {

  protected lazy final val currentDeserializer = new DeviceHelloDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[DeviceHello]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = DeviceHello.messageCode

  override def deserialize(is: InputStream, expectedMessageNumber: Int): DeviceHello = deserialize(is, is.read, expectedMessageNumber)

  protected def deserialize(is: InputStream, serializationVersion: Int, expectedMessageNumber: Int): DeviceHello = deserializers.get(serializationVersion) match {
    case Some(deserializer) => deserializer.deserialize(is, expectedMessageNumber)
    case None               => throw SerializationVersionNotSupportedException(serializationVersion)
  }
}

class DeviceHelloDeserializerVersion1 extends Deserializer[DeviceHello] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream, expectedMessageNumber: Int): DeviceHello = {

    validateMessageNumber(is.read, expectedMessageNumber)

    val sparkDeviceIdLength = is.read
    val sparkDeviceIdAsByteArray = new Array[Byte](sparkDeviceIdLength)
    is.read(sparkDeviceIdAsByteArray)
    val sparkDeviceId = new String(sparkDeviceIdAsByteArray)
    new DeviceHello(sparkDeviceId)
  }
}
