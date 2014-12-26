package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.fromdevice.DigitalPinValue
import as.sparkanta.device.config.pin.{ DigitalPin, DigitalPinValue => DigitalPinValueConfig }

class DigitalPinDeserializer extends Deserializer[DigitalPinValue] {

  protected lazy final val currentDeserializer = new DigitalPinDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[DigitalPinValue]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = DigitalPinValue.messageCode

  override def deserialize(is: InputStream, expectedMessageNumber: Int): DigitalPinValue = deserialize(is, is.read, expectedMessageNumber)

  protected def deserialize(is: InputStream, serializationVersion: Int, expectedMessageNumber: Int): DigitalPinValue = deserializers.get(serializationVersion) match {
    case Some(deserializer) => deserializer.deserialize(is, expectedMessageNumber)
    case None               => throw SerializationVersionNotSupportedException(serializationVersion)
  }
}

class DigitalPinDeserializerVersion1 extends Deserializer[DigitalPinValue] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream, expectedMessageNumber: Int): DigitalPinValue = {

    validateMessageNumber(is.read, expectedMessageNumber)

    val pin = DigitalPin(is.read)
    val digitalPinValue = DigitalPinValueConfig(is.read)

    new DigitalPinValue(pin, digitalPinValue)
  }
}
