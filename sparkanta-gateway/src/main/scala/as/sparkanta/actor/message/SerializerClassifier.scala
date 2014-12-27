package as.sparkanta.actor.message

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.message.SendMessageToDevice

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class SerializerClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: SendMessageToDevice => Some(a)
    case _                      => None
  }
}