package as.sparkanta.actor.device1.message.deserializer

import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor.tcp.serversocket.ServerSocket
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.gateway.{ Device => DeviceSpec }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DeserializerClassifier extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {
    case _: Socket.NewData                => Some(messageWithSender)
    case _: ServerSocket.NewConnection    => Some(messageWithSender)
    case _: DeviceSpec.StartErrorResult   => Some(messageWithSender)
    case _: DeviceSpec.Stopped            => Some(messageWithSender)
    case _: DeviceSpec.IdentifiedDeviceUp => Some(messageWithSender)
    case _                                => None
  }
}