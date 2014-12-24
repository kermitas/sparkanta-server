package as.sparkanta.device.message.serialize

import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }
import java.io.OutputStream
import as.sparkanta.device.message.{ DeviceHello, Disconnect, Ping, Pong, GatewayHello, ServerHello, PinConfiguration, DigitalPinValue, AnalogPinValue }

class Serializers extends Serializer[MessageToDeviceMarker] {

  protected final val deviceHelloSerializer = new DeviceHelloSerializer
  protected final val disconnectSerializer = new DisconnectSerializer
  protected final val pingSerializer = new PingSerializer
  protected final val pongSerializer = new PongSerializer
  protected final val gatewayHelloSerializer = new GatewayHelloSerializer
  protected final val serverHelloSerializer = new ServerHelloSerializer
  protected final val pinConfigurationSerializer = new PinConfigurationSerializer
  protected final val digitalPinValueSerializer = new DigitalPinValueSerializer
  protected final val analogPinValueSerializer = new AnalogPinValueSerializer

  override def serialize(messageToDevice: MessageToDeviceMarker, os: OutputStream, messageNumber: Int) = messageToDevice match {
    case deviceHello: DeviceHello           => deviceHelloSerializer.serialize(deviceHello, os, messageNumber)
    case disconnect: Disconnect             => disconnectSerializer.serialize(disconnect, os, messageNumber)
    case ping: Ping                         => pingSerializer.serialize(ping, os, messageNumber)
    case pong: Pong                         => pongSerializer.serialize(pong, os, messageNumber)
    case gatewayHello: GatewayHello         => gatewayHelloSerializer.serialize(gatewayHello, os, messageNumber)
    case serverHello: ServerHello           => serverHelloSerializer.serialize(serverHello, os, messageNumber)
    case pinConfiguration: PinConfiguration => pinConfigurationSerializer.serialize(pinConfiguration, os, messageNumber)
    case digitalPinValue: DigitalPinValue   => digitalPinValueSerializer.serialize(digitalPinValue, os, messageNumber)
    case analogPinValue: AnalogPinValue     => analogPinValueSerializer.serialize(analogPinValue, os, messageNumber)
    case unknownMessageToDevice             => throw new Exception(s"Unknown object ${unknownMessageToDevice.getClass.getSimpleName}, don't know how to serialize.")
  }
}
