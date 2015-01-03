package as.sparkanta.actor.device

import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor.tcp.socket.Socket

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DeviceMessageSenderClassifier extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {
    case _: Socket.ListeningStarted => Some(messageWithSender)
    case _                          => None
  }
}
