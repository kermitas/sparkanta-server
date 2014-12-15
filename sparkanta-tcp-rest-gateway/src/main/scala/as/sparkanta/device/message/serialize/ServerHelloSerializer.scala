package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.ServerHello

class ServerHelloSerializer extends ServerHelloSerializerVersion1

object ServerHelloSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class ServerHelloSerializerVersion1 extends Serializer[ServerHello] {

  import ServerHelloSerializerVersion1._

  override def serialize(gatewayHello: ServerHello, os: OutputStream): Unit = {
    os.write(ServerHello.messageCode)
    os.write(serializationVersion)
  }
}
