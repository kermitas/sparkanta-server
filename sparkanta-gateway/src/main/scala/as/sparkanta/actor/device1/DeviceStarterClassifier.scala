package as.sparkanta.actor.device1

import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor.tcp.serversocket.ServerSocket

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DeviceStarterClassifier extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {
    case _: ServerSocket.NewConnection => Some(messageWithSender)
    case _                             => None
  }
}