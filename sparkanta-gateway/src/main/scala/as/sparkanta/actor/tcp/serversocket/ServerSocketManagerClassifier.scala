/*
package as.sparkanta.actor.tcp.serversocket

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.message.{ ListenAt, ListenAtResult }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class ServerSocketManagerClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: ListenAt       => Some(a)
    case a: ListenAtResult => Some(a)
    case _                 => None
  }
}
*/ 