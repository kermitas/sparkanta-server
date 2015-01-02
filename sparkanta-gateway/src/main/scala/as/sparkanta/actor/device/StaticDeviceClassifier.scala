package as.sparkanta.actor.device

import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor.tcp.serversocket.ServerSocket
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.actor.message.MessageDataAccumulator

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class StaticDeviceClassifier extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {
    case _: ServerSocket.NewConnection => Some(messageWithSender)
    case _: Socket.ListeningStopped    => Some(messageWithSender)
    //case _: MessageDataAccumulator.MessageDataAccumulationResult => Some(messageWithSender)
    case _                             => None
  }
}
