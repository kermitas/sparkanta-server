package as.sparkanta.ama.actor.tcp.message

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.ama.actor.tcp.connection.TcpConnectionHandler

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class OutgoingMessageListenerClassifier(runtimeId: Long) extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    //case a: ????? if a.runtimeId == runtimeId => Some(a) TODO filter messages of some type that are dedicated to send to this device
    case _ => None
  }
}