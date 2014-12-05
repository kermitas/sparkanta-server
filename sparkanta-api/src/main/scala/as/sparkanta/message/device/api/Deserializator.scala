package as.sparkanta.message.device.api

import java.io.{ InputStream, ByteArrayInputStream }

trait Deserializator[+T <: MessageFormDevice] {
  def commandCode: Int
  def deserialize(byteArray: Array[Byte]): T = deserialize(new ByteArrayInputStream(byteArray))
  def deserialize(is: InputStream): T
}
