package as.sparkanta.device.message.deserialize

import java.io.{ InputStream, DataInputStream }
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.fromdevice.AnalogPinValue
import as.sparkanta.device.config.pin.AnalogPin

class AnalogPinDeserializer extends Deserializer[AnalogPinValue] {

  protected lazy final val currentDeserializer = new AnalogPinDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[AnalogPinValue]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = AnalogPinValue.messageCode

  override def deserialize(is: InputStream, expectedMessageNumber: Int): AnalogPinValue = deserialize(is, is.read, expectedMessageNumber)

  protected def deserialize(is: InputStream, serializationVersion: Int, expectedMessageNumber: Int): AnalogPinValue = deserializers.get(serializationVersion) match {
    case Some(deserializer) => deserializer.deserialize(is, expectedMessageNumber)
    case None               => throw SerializationVersionNotSupportedException(serializationVersion)
  }
}

class AnalogPinDeserializerVersion1 extends Deserializer[AnalogPinValue] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream, expectedMessageNumber: Int): AnalogPinValue = {

    validateMessageNumber(is.read, expectedMessageNumber)

    val pin = AnalogPin(is.read)
    val analogPinValue = new DataInputStream(is).readChar

    new AnalogPinValue(pin, analogPinValue)
  }
}
