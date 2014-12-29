package as.sparkanta.actor2.message

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.actor2.message.MessageDataAccumulator.{ AccumulateMessageData, AccumulateMessageDataFromBroadcaster, ClearData }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class MessageDataAccumulatorClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: AccumulateMessageData => Some(new AccumulateMessageDataFromBroadcaster(a))
    case a: ClearData             => Some(a)
    case _                        => None
  }
}