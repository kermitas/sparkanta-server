package as.sparkanta.gateway

import akka.actor.ActorRef
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message, OutgoingMessage, IncomingMessage }
import as.sparkanta.device.message.todevice.MessageToDevice
import as.sparkanta.device.message.fromdevice.MessageFromDevice
import as.sparkanta.device.DeviceInfo
import scala.net.IdentifiedConnectionInfo

object Device {
  class Start(val connectionInfo: IdentifiedConnectionInfo, val akkaSocketTcpActor: ActorRef, val maximumQueuedMessagesToSend: Long, val deviceIdentificationTimeoutInMs: Long, val pingPongSpeedTestTimeInMs: Option[Long]) extends IncomingReplyableMessage
  abstract class StartResult(val optionalException: Option[Exception], start: Start, startSender: ActorRef) extends OutgoingReplyOn1Message(start, startSender)
  class StartSuccessResult(start: Start, startSender: ActorRef) extends StartResult(None, start, startSender)
  class StartErrorResult(val exception: Exception, start: Start, startSender: ActorRef) extends StartResult(Some(exception), start, startSender)
  class Started(start: Start, startSender: ActorRef) extends OutgoingReplyOn1Message(start, startSender)

  class Stop(val optionalId: Option[Long], val cause: Exception) extends IncomingReplyableMessage
  class StopDevice(val id: Long, cause: Exception) extends Stop(Some(id), cause)
  class StopAllDevices(cause: Exception) extends Stop(None, cause)
  abstract class StopResult(val optionalException: Option[Exception], stop: Stop, stopSender: ActorRef) extends OutgoingReplyOn1Message(stop, stopSender)
  class StopSuccessResult(stop: Stop, stopSender: ActorRef) extends StopResult(None, stop, stopSender)
  class StopErrorResult(val exception: Exception, stop: Stop, stopSender: ActorRef) extends StopResult(Some(exception), stop, stopSender)
  class Stopped(val deviceStopType: StopType, start: Start, startSender: ActorRef) extends OutgoingReplyOn1Message(start, startSender)

  class IdentifiedDeviceUp(val deviceInfo: DeviceInfo, start: Start, startSender: ActorRef) extends OutgoingReplyOn1Message(start, startSender) with ForwardToRestServer
  class IdentifiedDeviceDown(val deviceInfo: DeviceInfo, val deviceStopType: StopType, val timeInSystemInMs: Long, start: Start, startSender: ActorRef) extends OutgoingReplyOn1Message(start, startSender) with ForwardToRestServer

  class NewMessage(val deviceInfo: DeviceInfo, val messageFromDevice: MessageFromDevice) extends OutgoingMessage with ForwardToRestServer

  class SendMessage(val id: Long, val messageToDevice: MessageToDevice, val ack: AckType) extends IncomingReplyableMessage
  abstract class SendMessageResult(val optionalException: Option[Exception], sendMessage: SendMessage, sendMessageSender: ActorRef) extends OutgoingReplyOn1Message(sendMessage, sendMessageSender)
  class SendMessageSuccessResult(sendMessage: SendMessage, sendMessageSender: ActorRef) extends SendMessageResult(None, sendMessage, sendMessageSender)
  class SendMessageErrorResult(val exception: Exception, sendMessage: SendMessage, sendMessageSender: ActorRef) extends SendMessageResult(Some(exception), sendMessage, sendMessageSender)

  // TODO how to transform stop type from socket to this type?
  sealed trait StopType extends Serializable
  object StoppedByRemoteSide extends StopType
  sealed trait StoppedByLocalSide extends StopType
  class StoppedBecauseOfLocalSideException(val exception: Exception) extends StoppedByLocalSide
  class StoppedBecauseOfLocalSideRequest(val stop: Stop, val stopSender: ActorRef) extends StoppedByLocalSide
}

