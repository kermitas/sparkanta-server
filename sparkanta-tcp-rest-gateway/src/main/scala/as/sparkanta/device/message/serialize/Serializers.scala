package as.sparkanta.device.message.serialize

import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }
import java.io.OutputStream
import as.sparkanta.device.message.{ DeviceHello, Disconnect, Ping, Pong }

class Serializers extends Serializer[MessageToDeviceMarker] {

  override def serialize(messageToDevice: MessageToDeviceMarker, os: OutputStream) = messageToDevice match {
    case deviceHello: DeviceHello => new DeviceHelloSerializer().serialize(deviceHello, os)
    case disconnect: Disconnect   => new DisconnectSerializer().serialize(disconnect, os)
    case ping: Ping               => new PingSerializer().serialize(ping, os)
    case pong: Pong               => new PongSerializer().serialize(pong, os)
    case unknownMessageToDevice   => throw new Exception(s"Unknown object ${unknownMessageToDevice.getClass.getSimpleName}, don't know how to serialize.")
  }
}
