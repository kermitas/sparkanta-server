package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.AnalogPinValue

class AnalogPinValueSerializer extends AnalogPinValueSerializerVersion1

object AnalogPinValueSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class AnalogPinValueSerializerVersion1 extends Serializer[AnalogPinValue] {

  import DigitalPinValueSerializerVersion1._

  override def serialize(analogPinValue: AnalogPinValue, os: OutputStream, messageNumber: Int): Unit = {
    writeHeader(os, AnalogPinValue.messageCode, serializationVersion, messageNumber, analogPinValue.ackType)

    os.write(analogPinValue.pin.pinNumber)
    os.write(analogPinValue.pinValue)
  }
}
