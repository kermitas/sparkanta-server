package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.DeviceHello

class DeviceHelloSerializer extends DeviceHelloSerializerVersion1

object DeviceHelloSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class DeviceHelloSerializerVersion1 extends Serializer[DeviceHello] {

  import DeviceHelloSerializerVersion1._

  override def serialize(deviceHello: DeviceHello, os: OutputStream): Unit = {
    os.write(DeviceHello.commandCode)
    os.write(serializationVersion)
    os.write(deviceHello.sparkDeviceId.length)
    os.write(deviceHello.sparkDeviceId.getBytes)
  }
}
