package as.sparkanta.device.message.deserialize

import java.io.{ InputStream, DataInputStream }
import scala.io.SerializationVersionNotSupportedException
import as.sparkanta.device.message.AnalogPinValue
import as.sparkanta.device.config.AnalogPin

class AnalogPinDeserializer extends Deserializer[AnalogPinValue] {

  protected lazy final val currentDeserializer = new AnalogPinDeserializerVersion1

  protected lazy final val deserializers = Map[Int, Deserializer[AnalogPinValue]](
    1 -> currentDeserializer
  )

  override def messageCode: Int = AnalogPinValue.messageCode

  override def deserialize(is: InputStream): AnalogPinValue = deserialize(is, is.read)

  protected def deserialize(is: InputStream, version: Int): AnalogPinValue = deserializers.get(version) match {
    case Some(deserializer) => deserializer.deserialize(is)
    case None               => throw SerializationVersionNotSupportedException(version)
  }
}

class AnalogPinDeserializerVersion1 extends Deserializer[AnalogPinValue] {
  override def messageCode: Int = ???

  override def deserialize(is: InputStream): AnalogPinValue = {

    val pin = AnalogPin(is.read)
    val analogPinValue = new DataInputStream(is).readChar

    new AnalogPinValue(pin, analogPinValue)
  }
}
