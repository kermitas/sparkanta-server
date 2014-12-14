package as.sparkanta.actor.pongreplier

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.device.message.Ping
import as.sparkanta.gateway.message.MessageFromDevice

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class PongReplierClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: MessageFromDevice if a.messageFromDevice.isInstanceOf[Ping] => Some(a.deviceInfo.remoteAddress.id)
    case _ => None
  }
}