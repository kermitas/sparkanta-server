package as.sparkanta.device.message.deserialize

import java.io.{ InputStream, ByteArrayInputStream }
import as.sparkanta.device.message.fromdevice.MessageFormDevice

trait Deserializer[+T <: MessageFormDevice] {

  var messageNumber = 0

  def messageCode: Int

  def deserialize(byteArray: Array[Byte]): T = deserialize(new ByteArrayInputStream(byteArray))

  def deserialize(is: InputStream): T = {
    val result = deserialize(is, messageNumber)
    messageNumber += 1
    if (messageNumber > 255) messageNumber = 0
    result
  }

  def deserialize(is: InputStream, expectedMessageNumber: Int): T

  protected def validateMessageNumber(messageNumber: Int, expectedMessageNumber: Int): Unit = {
    if (messageNumber != expectedMessageNumber) throw new Exception(s"Received message number $messageNumber should be $expectedMessageNumber.")
  }
}
