package as.sparkanta.message.device.api

import java.io.{ InputStream, ByteArrayInputStream }

class Deserializators(protected final val deserializators: Seq[Deserializator[MessageFormDevice]]) {

  def this() = this(Seq(new HelloDeserializator))

  def deserialize[T <: MessageFormDevice](byteArray: Array[Byte]): T = deserialize[T](new ByteArrayInputStream(byteArray))

  def deserialize[T <: MessageFormDevice](is: InputStream): T = deserialize[T](is, is.read)

  protected def deserialize[T <: MessageFormDevice](is: InputStream, commandCode: Int): T =
    deserializators.find(_.commandCode == commandCode).map(_.deserialize(is).asInstanceOf[T]).getOrElse(throw new Exception(s"Unknown command $commandCode code."))

}
