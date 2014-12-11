package as.sparkanta.actor.message.outgoing

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.server.message.MessageToDevice

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class OutgoingMessageSerializerClassifier(remoteAddressId: Long) extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: MessageToDevice if a.remoteAddressId == remoteAddressId => Some(a)
    case _ => None
  }
}