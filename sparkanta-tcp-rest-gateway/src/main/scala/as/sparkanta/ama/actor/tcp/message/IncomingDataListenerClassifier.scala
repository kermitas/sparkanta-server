package as.sparkanta.ama.actor.tcp.message

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.gateway.message.DataFromDevice

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class IncomingDataListenerClassifier(runtimeId: Long) extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: DataFromDevice if a.runtimeId == runtimeId => Some(a.data)
    case _ => None
  }
}