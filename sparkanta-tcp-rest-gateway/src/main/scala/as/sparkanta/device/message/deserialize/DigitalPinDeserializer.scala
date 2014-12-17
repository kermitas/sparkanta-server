package as.sparkanta.device.message.deserialize

import java.io.InputStream
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.DigitalPinValue
import as.sparkanta.device.config.{ DigitalPin, DigitalPinValue => DigitalPinValueConfig }

class DigitalPinDeserializer extends Deserializer[DigitalPinValue] {

  protected lazy final val currentDeserializer = new DigitalPinDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[DigitalPinValue]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = DigitalPinValue.messageCode

  override def deserialize(is: InputStream): DigitalPinValue = deserialize(is, is.read)

  protected def deserialize(is: InputStream, version: Int): DigitalPinValue = deserializers.get(version) match {
    case Some(deserializer) => deserializer.deserialize(is)
    case None               => throw SerializationVersionNotSupportedException(version)
  }
}

class DigitalPinDeserializerVersion1 extends Deserializer[DigitalPinValue] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream): DigitalPinValue = {

    val pin = DigitalPin(is.read)
    val digitalPin = DigitalPinValueConfig(is.read)

    new DigitalPinValue(pin, digitalPin)
  }
}
