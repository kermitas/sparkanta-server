package as.sparkanta.device.message

import java.io.InputStream

class Deserializators(protected final val deserializators: Seq[Deserializator[MessageFormDevice]]) extends Deserializator[MessageFormDevice] {

  def this() = this(
    Seq(
      new HelloDeserializator
    )
  )

  override def commandCode: Int = ???

  override def deserialize(is: InputStream): MessageFormDevice = deserialize(is, is.read)

  protected def deserialize(is: InputStream, commandCode: Int): MessageFormDevice =
    deserializators.find(_.commandCode == commandCode).map(_.deserialize(is)).getOrElse(throw new Exception(s"Unknown command $commandCode code, can not deserialize."))

}
