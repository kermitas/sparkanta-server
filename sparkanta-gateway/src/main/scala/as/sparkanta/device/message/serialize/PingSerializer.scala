package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.todevice.Ping
import as.sparkanta.device.AckType

class PingSerializer extends PingSerializerVersion1

object PingSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class PingSerializerVersion1 extends Serializer[Ping] {

  import PingSerializerVersion1._

  override def serialize(ping: Ping, ackType: AckType, os: OutputStream, messageNumber: Int): Unit = {
    writeHeader(os, Ping.messageCode, serializationVersion, messageNumber, ackType)
  }
}
