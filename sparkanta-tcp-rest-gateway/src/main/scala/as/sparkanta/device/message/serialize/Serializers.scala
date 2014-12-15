package as.sparkanta.device.message.serialize

import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }
import java.io.OutputStream
import as.sparkanta.device.message.{ DeviceHello, Disconnect, Ping, Pong, GatewayHello }

class Serializers extends Serializer[MessageToDeviceMarker] {

  protected final val deviceHelloSerializer = new DeviceHelloSerializer
  protected final val disconnectSerializer = new DisconnectSerializer
  protected final val pingSerializer = new PingSerializer
  protected final val pongSerializer = new PongSerializer
  protected final val gatewayHelloSerializer = new GatewayHelloSerializer

  override def serialize(messageToDevice: MessageToDeviceMarker, os: OutputStream) = messageToDevice match {
    case deviceHello: DeviceHello   => deviceHelloSerializer.serialize(deviceHello, os)
    case disconnect: Disconnect     => disconnectSerializer.serialize(disconnect, os)
    case ping: Ping                 => pingSerializer.serialize(ping, os)
    case pong: Pong                 => pongSerializer.serialize(pong, os)
    case gatewayHello: GatewayHello => gatewayHelloSerializer.serialize(gatewayHello, os)
    case unknownMessageToDevice     => throw new Exception(s"Unknown object ${unknownMessageToDevice.getClass.getSimpleName}, don't know how to serialize.")
  }
}
