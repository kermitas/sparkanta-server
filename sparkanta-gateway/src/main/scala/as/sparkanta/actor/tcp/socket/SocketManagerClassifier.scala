package as.sparkanta.actor.tcp.socket

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.message.NewIncomingConnection

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class SocketManagerClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: NewIncomingConnection => Some(a)
    case _                        => None
  }
}