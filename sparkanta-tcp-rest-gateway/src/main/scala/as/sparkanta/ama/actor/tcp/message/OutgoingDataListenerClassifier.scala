package as.sparkanta.ama.actor.tcp.message

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.gateway.message.DataToDevice
import akka.util.ByteString

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class OutgoingDataListenerClassifier(runtimeId: Long) extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: DataToDevice if a.runtimeId == runtimeId => Some(a.data)
    case _ => None
  }
}