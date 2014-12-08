package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.Hello

class HelloSerializer extends HelloSerializerVersion1

class HelloSerializerVersion1 extends Serializer[Hello] {

  override def serialize(hello: Hello, os: OutputStream): Unit = {
    os.write(Hello.commandCode)
    os.write(1) // serialization version
    os.write(hello.sparkDeviceId.length)
    os.write(hello.sparkDeviceId.getBytes)
  }
}
