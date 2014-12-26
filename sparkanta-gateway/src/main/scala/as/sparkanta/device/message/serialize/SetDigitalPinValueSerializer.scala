package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.todevice.SetDigitalPinValue

class SetDigitalPinValueSerializer extends SetDigitalPinValueSerializerVersion1

object SetDigitalPinValueSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class SetDigitalPinValueSerializerVersion1 extends Serializer[SetDigitalPinValue] {

  import SetDigitalPinValueSerializerVersion1._

  override def serialize(setDigitalPinValue: SetDigitalPinValue, os: OutputStream, messageNumber: Int): Unit = {
    writeHeader(os, SetDigitalPinValue.messageCode, serializationVersion, messageNumber, setDigitalPinValue.ackType)

    os.write(setDigitalPinValue.pin.pinNumber)
    os.write(setDigitalPinValue.pinValue.pinValue)
  }
}
