package as.sparkanta.ama.actor.restforwarder

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.gateway.message.ForwardToRestServer

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class RestForwarderClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: ForwardToRestServer => Some(a)
    case _                      => None
  }
}