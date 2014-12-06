package as.sparkanta.device.message

import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }
import java.io.{ ByteArrayOutputStream, OutputStream }

trait Serializer[T <: MessageToDeviceMarker] {

  def serialize(messageToDevice: T): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    serialize(messageToDevice, baos)
    baos.toByteArray
  }

  def serialize(messageToDevice: T, os: OutputStream)
}
