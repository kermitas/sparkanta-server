package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.Pong

class PongSerializer extends PongSerializerVersion1

object PongSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class PongSerializerVersion1 extends Serializer[Pong] {

  import PingSerializerVersion1._

  override def serialize(pong: Pong, os: OutputStream, messageNumber: Int): Unit = {
    writeHeader(os, Pong.messageCode, serializationVersion, messageNumber, pong.ackType)
  }
}
