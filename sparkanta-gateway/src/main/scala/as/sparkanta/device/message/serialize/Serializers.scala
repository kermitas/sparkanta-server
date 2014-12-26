package as.sparkanta.device.message.serialize

import as.sparkanta.device.message.todevice.MessageToDevice
import java.io.OutputStream
import as.sparkanta.device.message.todevice.{ Disconnect, Ping, GatewayHello, ServerHello, PinConfiguration, SetDigitalPinValue, SetAnalogPinValue }

class Serializers extends Serializer[MessageToDevice] {

  protected final val disconnectSerializer = new DisconnectSerializer
  protected final val pingSerializer = new PingSerializer
  protected final val gatewayHelloSerializer = new GatewayHelloSerializer
  protected final val serverHelloSerializer = new ServerHelloSerializer
  protected final val pinConfigurationSerializer = new PinConfigurationSerializer
  protected final val setDigitalPinValueSerializer = new SetDigitalPinValueSerializer
  protected final val setAnalogPinValueSerializer = new SetAnalogPinValueSerializer

  override def serialize(messageToDevice: MessageToDevice, os: OutputStream, messageNumber: Int) = messageToDevice match {
    case disconnect: Disconnect                 => disconnectSerializer.serialize(disconnect, os, messageNumber)
    case ping: Ping                             => pingSerializer.serialize(ping, os, messageNumber)
    case gatewayHello: GatewayHello             => gatewayHelloSerializer.serialize(gatewayHello, os, messageNumber)
    case serverHello: ServerHello               => serverHelloSerializer.serialize(serverHello, os, messageNumber)
    case pinConfiguration: PinConfiguration     => pinConfigurationSerializer.serialize(pinConfiguration, os, messageNumber)
    case setDigitalPinValue: SetDigitalPinValue => setDigitalPinValueSerializer.serialize(setDigitalPinValue, os, messageNumber)
    case setAnalogPinValue: SetAnalogPinValue   => setAnalogPinValueSerializer.serialize(setAnalogPinValue, os, messageNumber)
    case unknownMessageToDevice                 => throw new Exception(s"Unknown object ${unknownMessageToDevice.getClass.getSimpleName}, don't know how to serialize.")
  }
}
