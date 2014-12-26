package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.todevice.GatewayHello
import as.sparkanta.device.AckType

class GatewayHelloSerializer extends GatewayHelloSerializerVersion1

object GatewayHelloSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class GatewayHelloSerializerVersion1 extends Serializer[GatewayHello] {

  import GatewayHelloSerializerVersion1._

  override def serialize(gatewayHello: GatewayHello, ackType: AckType, os: OutputStream, messageNumber: Int): Unit = {
    writeHeader(os, GatewayHello.messageCode, serializationVersion, messageNumber, ackType)
  }
}
