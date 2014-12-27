package as.sparkanta.actor.message

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.message.{ NewDataFromDevice, NewIncomingConnection, ConnectionClosed }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class MessageDataCollectorClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: NewDataFromDevice     => Some(a)
    case a: NewIncomingConnection => Some(a)
    case a: ConnectionClosed      => Some(a)
    case _                        => None
  }
}