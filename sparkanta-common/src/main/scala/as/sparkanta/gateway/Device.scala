package as.sparkanta.gateway

import akka.actor.ActorRef
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message, OutgoingMessage, IncomingMessage }
import as.sparkanta.device.message.todevice.MessageToDevice
import as.sparkanta.device.message.fromdevice.MessageFormDevice
import as.sparkanta.device.DeviceInfo
import scala.net.IdentifiedConnectionInfo

object Device {

  class Start(connectionInfo: IdentifiedConnectionInfo, val akkaSocketTcpActor: ActorRef, val maximumQueuedMessagesToSend: Long) extends IncomingReplyableMessage
  abstract class StartResult(val optionalException: Option[Exception], start: Start, startSender: ActorRef) extends OutgoingReplyOn1Message(start, startSender)
  class StartSuccessResult(start: Start, startSender: ActorRef) extends StartResult(None, start, startSender)
  class StartErrorResult(val exception: Exception, start: Start, startSender: ActorRef) extends StartResult(Some(exception), start, startSender)
  class DeviceStarted(start: Start, startSender: ActorRef) extends OutgoingReplyOn1Message(start, startSender)

  class Stop(id: Long) extends IncomingReplyableMessage
  abstract class StopResult(val optionalException: Option[Exception], stop: Stop, stopSender: ActorRef) extends OutgoingReplyOn1Message(stop, stopSender)
  class StopSuccessResult(stop: Stop, stopSender: ActorRef) extends StopResult(None, stop, stopSender)
  class StopErrorResult(val exception: Exception, stop: Stop, stopSender: ActorRef) extends StopResult(Some(exception), stop, stopSender)
  class DeviceStopped(val deviceStopType: DeviceStopType, start: Start, startSender: ActorRef) extends OutgoingReplyOn1Message(start, startSender)

  class IdentifiedDeviceUp(val deviceInfo: DeviceInfo, start: Start, startSender: ActorRef) extends OutgoingReplyOn1Message(start, startSender) with ForwardToRestServer
  class IdentifiedDeviceDown(val deviceInfo: DeviceInfo, val deviceStopType: DeviceStopType, val timeInSystemInMs: Long, start: Start, startSender: ActorRef) extends OutgoingReplyOn1Message(start, startSender) with ForwardToRestServer

  class NewMessage(val deviceInfo: DeviceInfo, val messageFromDevice: MessageFormDevice) extends OutgoingMessage with ForwardToRestServer

  class SendMessage(val id: Long, val messageToDevice: MessageToDevice, val ack: AckType) extends IncomingReplyableMessage
  abstract class SendMessageResult(val optionalException: Option[Exception], sendMessage: SendMessage, sendMessageSender: ActorRef) extends OutgoingReplyOn1Message(sendMessage, sendMessageSender)
  class SendMessageSuccessResult(sendMessage: SendMessage, sendMessageSender: ActorRef) extends SendMessageResult(None, sendMessage, sendMessageSender)
  class SendMessageErrorResult(val exception: Exception, sendMessage: SendMessage, sendMessageSender: ActorRef) extends SendMessageResult(Some(exception), sendMessage, sendMessageSender)

  sealed trait DeviceStopType extends Serializable
  class DeviceStoppedBecauseOfException(val exception: Exception) extends DeviceStopType
  class DeviceStoppedBecauseOfDisconnectDevice(val disconnectDevice: DisconnectDevice) extends DeviceStopType
  class DeviceStoppedBecauseOfDisconnectAllDevices(val disconnectAllDevices: DisconnectAllDevices) extends DeviceStopType

  class DisconnectDevice(val id: Long, val cause: Exception) extends IncomingMessage
  class DisconnectAllDevices(val cause: Exception) extends IncomingMessage
}

