package as.sparkanta.device.message.serialize

import as.sparkanta.device.message.todevice.MessageToDevice
import java.io.OutputStream
import as.sparkanta.device.message.todevice.{ Disconnect, Ping, GatewayHello, ServerHello, PinConfiguration, SetDigitalPinValue, SetAnalogPinValue }
import as.sparkanta.device.AckType

class Serializers extends Serializer[MessageToDevice] {

  protected final val disconnectSerializer = new DisconnectSerializer
  protected final val pingSerializer = new PingSerializer
  protected final val gatewayHelloSerializer = new GatewayHelloSerializer
  protected final val serverHelloSerializer = new ServerHelloSerializer
  protected final val pinConfigurationSerializer = new PinConfigurationSerializer
  protected final val setDigitalPinValueSerializer = new SetDigitalPinValueSerializer
  protected final val setAnalogPinValueSerializer = new SetAnalogPinValueSerializer

  override def serialize(messageToDevice: MessageToDevice, ackType: AckType, os: OutputStream, messageNumber: Int) = messageToDevice match {
    case disconnect: Disconnect                 => disconnectSerializer.serialize(disconnect, ackType, os, messageNumber)
    case ping: Ping                             => pingSerializer.serialize(ping, ackType, os, messageNumber)
    case gatewayHello: GatewayHello             => gatewayHelloSerializer.serialize(gatewayHello, ackType, os, messageNumber)
    case serverHello: ServerHello               => serverHelloSerializer.serialize(serverHello, ackType, os, messageNumber)
    case pinConfiguration: PinConfiguration     => pinConfigurationSerializer.serialize(pinConfiguration, ackType, os, messageNumber)
    case setDigitalPinValue: SetDigitalPinValue => setDigitalPinValueSerializer.serialize(setDigitalPinValue, ackType, os, messageNumber)
    case setAnalogPinValue: SetAnalogPinValue   => setAnalogPinValueSerializer.serialize(setAnalogPinValue, ackType, os, messageNumber)
    case unknownMessageToDevice                 => throw new Exception(s"Don't know how to serialize object ${unknownMessageToDevice.getClass.getSimpleName}, $unknownMessageToDevice.")
  }
}
