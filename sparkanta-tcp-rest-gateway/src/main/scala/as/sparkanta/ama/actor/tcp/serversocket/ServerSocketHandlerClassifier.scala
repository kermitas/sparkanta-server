package as.sparkanta.ama.actor.tcp.serversocket

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.server.message.StopListeningAt

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class ServerSocketHandlerClassifier(listenIp: String, listenPort: Int) extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: StopListeningAt if a.listenPort == listenPort && a.listenIp.equals(listenIp) => Some(a)
    case _ => None
  }
}