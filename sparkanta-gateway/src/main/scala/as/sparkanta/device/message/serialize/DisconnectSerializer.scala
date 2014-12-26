package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.todevice.Disconnect
import as.sparkanta.device.AckType

class DisconnectSerializer extends DisconnectSerializerVersion1

object DisconnectSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class DisconnectSerializerVersion1 extends Serializer[Disconnect] {

  import DisconnectSerializerVersion1._

  override def serialize(disconnect: Disconnect, ackType: AckType, os: OutputStream, messageNumber: Int): Unit = {
    writeHeader(os, Disconnect.messageCode, serializationVersion, messageNumber, ackType)

    os.write(disconnect.delayBeforeNextConnectionAttemptInSeconds)
  }
}
