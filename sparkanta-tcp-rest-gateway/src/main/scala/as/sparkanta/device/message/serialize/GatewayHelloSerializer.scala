package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.GatewayHello

class GatewayHelloSerializer extends GatewayHelloSerializerVersion1

object GatewayHelloSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class GatewayHelloSerializerVersion1 extends Serializer[GatewayHello] {

  import GatewayHelloSerializerVersion1._

  override def serialize(gatewayHello: GatewayHello, os: OutputStream): Unit = {
    os.write(GatewayHello.messageCode)
    os.write(serializationVersion)
  }
}
