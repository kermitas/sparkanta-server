package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.todevice.{ ServerHello, DeviceAckType }

class ServerHelloSerializer extends ServerHelloSerializerVersion1

object ServerHelloSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class ServerHelloSerializerVersion1 extends Serializer[ServerHello] {

  import ServerHelloSerializerVersion1._

  override def serialize(serverHello: ServerHello, ackType: DeviceAckType, os: OutputStream, messageNumber: Int): Unit = {
    writeHeader(os, serverHello.messageCode, serializationVersion, messageNumber, ackType)
  }
}
