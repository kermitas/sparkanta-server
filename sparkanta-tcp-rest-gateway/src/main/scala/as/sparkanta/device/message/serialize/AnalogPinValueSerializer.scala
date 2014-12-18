package as.sparkanta.device.message.serialize

import java.io.{ OutputStream, DataOutputStream }
import as.sparkanta.device.message.AnalogPinValue

class AnalogPinValueSerializer extends AnalogPinValueSerializerVersion1

object AnalogPinValueSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class AnalogPinValueSerializerVersion1 extends Serializer[AnalogPinValue] {

  import DigitalPinValueSerializerVersion1._

  override def serialize(analogPinValue: AnalogPinValue, os: OutputStream): Unit = {
    os.write(AnalogPinValue.messageCode)
    os.write(serializationVersion)
    os.write(analogPinValue.pin.pinNumber)

    new DataOutputStream(os).writeChar(analogPinValue.pinValue)
  }
}
