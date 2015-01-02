package as.sparkanta.actor2.device

import as.akka.broadcaster.{ Classifier, MessageWithSender }
import as.sparkanta.actor2.tcp.serversocket.ServerSocket
import as.sparkanta.actor2.tcp.socket.Socket

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class StaticDeviceClassifier extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {
    case _: ServerSocket.NewConnection => Some(messageWithSender)
    case _: Socket.NewData             => Some(messageWithSender)
    case _: Socket.ListeningStopped    => Some(messageWithSender)
    case _                             => None
  }
}