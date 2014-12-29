package as.sparkanta.actor2.message.deserializer

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.actor2.message.deserializer.Deserializer.{ Deserialize, DeserializeFromBroadcaster }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DeserializerClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: Deserialize => Some(new DeserializeFromBroadcaster(a))
    case _              => None
  }
}