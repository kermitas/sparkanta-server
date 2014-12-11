package as.sparkanta.actor.message.outgoing

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.gateway.message.DataToDevice

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class OutgoingDataSenderClassifier(runtimeId: Long) extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: DataToDevice if a.runtimeId == runtimeId => Some(a)
    case _ => None
  }
}