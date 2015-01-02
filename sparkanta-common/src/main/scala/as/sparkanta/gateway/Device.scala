package as.sparkanta.gateway

import akka.actor.ActorRef
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message, OutgoingMessage, IncomingMessage }
import as.sparkanta.device.message.todevice.MessageToDevice
import as.sparkanta.device.message.fromdevice.MessageFormDevice
import as.sparkanta.device.DeviceInfo

object Device {

  class DeviceIsUp(val deviceInfo: DeviceInfo) extends OutgoingMessage with ForwardToRestServer
  class DeviceIsDown(val deviceInfo: DeviceInfo, val cause: Exception, val timeInSystemInMs: Long) extends OutgoingMessage with ForwardToRestServer

  class NewMessage(val deviceInfo: DeviceInfo, val messageFromDevice: MessageFormDevice) extends OutgoingMessage with ForwardToRestServer

  class SendMessage(val id: Long, val messageToDevice: MessageToDevice, val ack: AckType) extends IncomingReplyableMessage
  abstract class SendMessageResult(val exception: Option[Exception], sendMessage: SendMessage, sendMessageSender: ActorRef) extends OutgoingReplyOn1Message(sendMessage, sendMessageSender)
  class SendMessageSuccessResult(sendMessage: SendMessage, sendMessageSender: ActorRef) extends SendMessageResult(None, sendMessage, sendMessageSender)
  class SendMessageErrorResult(exception: Exception, sendMessage: SendMessage, sendMessageSender: ActorRef) extends SendMessageResult(Some(exception), sendMessage, sendMessageSender)

  class DisconnectDevice(val id: Long, val cause: Exception) extends IncomingMessage
  object DisconnectAllDevices extends IncomingMessage

  class DisconnectedOnDisconnectAllRequestException(id: Long) extends Exception(s"Device of remote address id $id was disconnected because of ${DisconnectAllDevices.getClass.getSimpleName}.")
}

