package as.sparkanta.ama.actor.tcp.message

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.ama.actor.tcp.connection.TcpConnectionHandler

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class MessageListenerClassifier(requiredSender: ActorRef) extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: TcpConnectionHandler.IncomingMessage if sender.equals(requiredSender) => Some(a)
    case _ => None
  }
}