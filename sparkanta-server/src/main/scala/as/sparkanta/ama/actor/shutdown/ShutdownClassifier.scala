package as.sparkanta.ama.actor.shutdown

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.ama.addon.inputstream.InputStreamText

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class ShutdownClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: InputStreamText => Some(a)
    case _                  => None
  }
}