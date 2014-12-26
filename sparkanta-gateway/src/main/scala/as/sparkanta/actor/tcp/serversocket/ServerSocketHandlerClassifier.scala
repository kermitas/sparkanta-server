/*
package as.sparkanta.actor.tcp.serversocket

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.server.message.StopListeningAt

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class ServerSocketHandlerClassifier(listenAddressId: Long) extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: StopListeningAt if a.listenAddressId == listenAddressId => Some(a)
    case _ => None
  }
}*/ 