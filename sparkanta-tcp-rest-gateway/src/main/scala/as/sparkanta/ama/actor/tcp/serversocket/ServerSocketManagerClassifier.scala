package as.sparkanta.ama.actor.tcp.serversocket

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.server.message.ListenAt

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class ServerSocketManagerClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: ListenAt => Some(a)
    case _           => None
  }
}