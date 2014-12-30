package as.sparkanta.actor2.tcp.serversocket

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.actor2.tcp.serversocket.ServerSocket.{ ListenAt, ListenAtFromBroadcaster, StopListeningAt }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class ServerSocketClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: ListenAt        => Some(new ListenAtFromBroadcaster(a))
    case a: StopListeningAt => Some(a)
    case _                  => None
  }
}