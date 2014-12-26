package as.sparkanta.device.message.serialize

import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }
import as.sparkanta.device.AckType
import java.io.{ ByteArrayOutputStream, OutputStream }

trait Serializer[T <: MessageToDeviceMarker] {

  var messageNumber = 0

  def serialize(messageToDevice: T): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    serialize(messageToDevice, baos)
    baos.toByteArray
  }

  def serialize(messageToDevice: T, os: OutputStream): Unit = {
    serialize(messageToDevice, os, messageNumber)
    messageNumber += 1
    if (messageNumber > 255) messageNumber = 0
  }

  def serialize(messageToDevice: T, os: OutputStream, messageNumber: Int)

  protected def writeHeader(os: OutputStream, messageCode: Int, serializationVersion: Int, messageNumber: Int, ackType: AckType): Unit = {
    os.write(messageCode)
    os.write(serializationVersion)
    os.write(messageNumber)
    os.write(ackType.ackType)
  }
}
