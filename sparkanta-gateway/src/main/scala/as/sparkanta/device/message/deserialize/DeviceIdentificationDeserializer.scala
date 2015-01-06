package as.sparkanta.device.message.deserialize

import java.io.{ InputStream, DataInputStream }
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.fromdevice.DeviceIdentification
import as.sparkanta.device.HardwareVersion

class DeviceIdentificationDeserializer extends Deserializer[DeviceIdentification] {

  protected lazy final val currentDeserializer = new DeviceIdentificationDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[DeviceIdentification]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = DeviceIdentification.messageCode

  override def deserialize(is: InputStream, expectedMessageNumber: Int): DeviceIdentification = deserialize(is, is.read, expectedMessageNumber)

  protected def deserialize(is: InputStream, serializationVersion: Int, expectedMessageNumber: Int): DeviceIdentification = deserializers.get(serializationVersion) match {
    case Some(deserializer) => deserializer.deserialize(is, expectedMessageNumber)
    case None               => throw SerializationVersionNotSupportedException(serializationVersion)
  }
}

class DeviceIdentificationDeserializerVersion1 extends Deserializer[DeviceIdentification] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream, expectedMessageNumber: Int): DeviceIdentification = {

    validateMessageNumber(is.read, expectedMessageNumber)

    val softwareVersion = is.read
    val hardwareVersion = HardwareVersion(is.read)
    val deviceUniqueId = new DataInputStream(is).readChar

    new DeviceIdentification(softwareVersion, hardwareVersion, deviceUniqueId)
  }
}