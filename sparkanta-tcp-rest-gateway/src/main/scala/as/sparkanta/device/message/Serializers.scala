package as.sparkanta.device.message

import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }
import java.io.OutputStream

class Serializers extends Serializer[MessageToDeviceMarker] {

  override def serialize(messageToDevice: MessageToDeviceMarker, os: OutputStream) = messageToDevice match {
    case hello: Hello           => new HelloSerializer().serialize(hello, os)
    case unknownMessageToDevice => throw new Exception(s"Unknown object ${unknownMessageToDevice.getClass.getSimpleName}, don't know how to serialize.")
  }
}
