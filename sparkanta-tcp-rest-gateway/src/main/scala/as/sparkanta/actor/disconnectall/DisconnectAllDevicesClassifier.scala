package as.sparkanta.actor.disconnectall

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.server.message.{ DisconnectAllDevices => DisconnectAllDevicesMessage }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DisconnectAllDevicesClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: DisconnectAllDevicesMessage => Some(a)
    case _                              => None
  }
}