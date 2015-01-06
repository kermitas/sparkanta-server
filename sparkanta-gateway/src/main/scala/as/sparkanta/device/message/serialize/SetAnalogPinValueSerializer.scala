package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.todevice.{ SetAnalogPinValue, DeviceAckType }

class SetAnalogPinValueSerializer extends SetAnalogPinValueSerializerVersion1

object SetAnalogPinValueSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class SetAnalogPinValueSerializerVersion1 extends Serializer[SetAnalogPinValue] {

  import SetAnalogPinValueSerializerVersion1._

  override def serialize(setAnalogPinValue: SetAnalogPinValue, ackType: DeviceAckType, os: OutputStream, messageNumber: Int): Unit = {
    writeHeader(os, setAnalogPinValue.messageCode, serializationVersion, messageNumber, ackType)

    os.write(setAnalogPinValue.pin.pinNumber)
    os.write(setAnalogPinValue.pinValue)
  }
}
