package as.sparkanta.device.message.serialize

import as.sparkanta.device.message.todevice.MessageToDevice
import as.sparkanta.device.AckType
import java.io.{ ByteArrayOutputStream, OutputStream }

trait Serializer[T <: MessageToDevice] {

  var messageNumber = 0

  def serialize(messageToDevice: T, ackType: AckType): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    serialize(messageToDevice, ackType, baos)
    baos.toByteArray
  }

  def serialize(messageToDevice: T, ackType: AckType, os: OutputStream): Unit = {
    serialize(messageToDevice, ackType, os, messageNumber)
    messageNumber += 1
    if (messageNumber > 255) messageNumber = 0
  }

  def serialize(messageToDevice: T, ackType: AckType, os: OutputStream, messageNumber: Int)

  protected def writeHeader(os: OutputStream, messageCode: Int, serializationVersion: Int, messageNumber: Int, ackType: AckType): Unit = {
    os.write(messageCode)
    os.write(serializationVersion)
    os.write(messageNumber)
    os.write(ackType.ackType)
  }
}
