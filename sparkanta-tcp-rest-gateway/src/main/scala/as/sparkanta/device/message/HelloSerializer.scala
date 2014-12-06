package as.sparkanta.device.message

import java.io.OutputStream

class HelloSerializer extends HelloSerializerVersion1

class HelloSerializerVersion1 extends Serializer[Hello] {

  override def serialize(hello: Hello, os: OutputStream): Unit = {
    os.write(Hello.commandCode)
    os.write(1)
    os.write(hello.softwareVersion)
    os.write(hello.sparkDeviceId.length)
    os.write(hello.sparkDeviceId.getBytes)
  }
}
