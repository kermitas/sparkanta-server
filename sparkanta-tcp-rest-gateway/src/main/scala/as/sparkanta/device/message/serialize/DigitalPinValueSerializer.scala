package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.DigitalPinValue

class DigitalPinValueSerializer extends DigitalPinValueSerializerVersion1

object DigitalPinValueSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class DigitalPinValueSerializerVersion1 extends Serializer[DigitalPinValue] {

  import DigitalPinValueSerializerVersion1._

  override def serialize(digitalPinValue: DigitalPinValue, os: OutputStream): Unit = {
    os.write(DigitalPinValue.messageCode)
    os.write(serializationVersion)
    os.write(digitalPinValue.pin.pinNumber)
    os.write(digitalPinValue.pinValue.pinValue)
  }
}
