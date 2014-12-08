package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.Hello

class HelloSerializer extends HelloSerializerVersion1

object HelloSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class HelloSerializerVersion1 extends Serializer[Hello] {

  import HelloSerializerVersion1._

  override def serialize(hello: Hello, os: OutputStream): Unit = {
    os.write(Hello.commandCode)
    os.write(serializationVersion)
    os.write(hello.sparkDeviceId.length)
    os.write(hello.sparkDeviceId.getBytes)
  }
}
