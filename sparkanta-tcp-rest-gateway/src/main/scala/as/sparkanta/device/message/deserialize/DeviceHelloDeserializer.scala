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

  override def deserialize(is: InputStream): DeviceHello = deserialize(is, is.read)

  protected def deserialize(is: InputStream, version: Int): DeviceHello = deserializers.get(version) match {
    case Some(deserializer) => deserializer.deserialize(is)
    case None               => throw SerializationVersionNotSupportedException(version)
  }
}

class DeviceHelloDeserializerVersion1 extends Deserializer[DeviceHello] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream): DeviceHello = {
    val sparkDeviceIdLength = is.read
    val sparkDeviceIdAsByteArray = new Array[Byte](sparkDeviceIdLength)
    is.read(sparkDeviceIdAsByteArray)
    val sparkDeviceId = new String(sparkDeviceIdAsByteArray)
    new DeviceHello(sparkDeviceId)
  }
}
