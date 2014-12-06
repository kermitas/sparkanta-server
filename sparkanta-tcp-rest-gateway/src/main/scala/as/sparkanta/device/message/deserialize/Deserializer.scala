package as.sparkanta.device.message.deserialize

import java.io.{ InputStream, ByteArrayInputStream }
import as.sparkanta.device.message.MessageFormDevice

trait Deserializer[+T <: MessageFormDevice] {
  def commandCode: Int
  def deserialize(byteArray: Array[Byte]): T = deserialize(new ByteArrayInputStream(byteArray))
  def deserialize(is: InputStream): T
}
