package as.sparkanta.actor2.message.serializer

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.actor2.message.serializer.Serializer.{ Serialize, SerializeFromBroadcaster }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class SerializerClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: Serialize => Some(new SerializeFromBroadcaster(a))
    case _            => None
  }
}