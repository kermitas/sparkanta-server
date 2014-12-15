package as.sparkanta.device.message.serialize

import java.io.OutputStream
import as.sparkanta.device.message.Ping

class PingSerializer extends PingSerializerVersion1

object PingSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class PingSerializerVersion1 extends Serializer[Ping] {

  import PingSerializerVersion1._

  override def serialize(ping: Ping, os: OutputStream): Unit = {
    os.write(Ping.messageCode)
    os.write(serializationVersion)
  }
}
