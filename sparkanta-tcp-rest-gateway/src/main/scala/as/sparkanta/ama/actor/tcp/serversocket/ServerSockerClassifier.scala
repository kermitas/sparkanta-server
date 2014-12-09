package as.sparkanta.ama.actor.tcp.serversocket

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class ServerSockerClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = None
}