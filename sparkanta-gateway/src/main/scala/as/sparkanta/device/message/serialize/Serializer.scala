package as.sparkanta.device.message.serialize

import as.sparkanta.device.message.todevice.{ MessageToDevice, DeviceAckType, NoAck }
import java.io.{ ByteArrayOutputStream, OutputStream }

trait Serializer[T <: MessageToDevice] {

  protected val baos = new ByteArrayOutputStream(300)
  protected var messageNumber = 0

  def serialize(messageToDevice: T, ackType: DeviceAckType = NoAck): Array[Byte] = {
    baos.reset
    serialize(messageToDevice, ackType, baos)
    baos.toByteArray
  }

  def serialize(messageToDevice: T, ackType: DeviceAckType, os: OutputStream): Unit = {
    serialize(messageToDevice, ackType, os, messageNumber)
    messageNumber += 1
    if (messageNumber > 255) messageNumber = 0
  }

  def serialize(messageToDevice: T, ackType: DeviceAckType, os: OutputStream, messageNumber: Int)

  protected def writeHeader(os: OutputStream, messageCode: Int, serializationVersion: Int, messageNumber: Int, ackType: DeviceAckType): Unit = {
    os.write(messageCode)
    os.write(serializationVersion)
    os.write(messageNumber)
    os.write(ackType.ackType)
  }
}
