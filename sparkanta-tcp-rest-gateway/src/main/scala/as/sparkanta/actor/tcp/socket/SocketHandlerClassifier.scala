package as.sparkanta.actor.tcp.socket

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.gateway.message.SparkDeviceIdWasIdentified

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class SocketHandlerClassifier(remoteAddressId: Long) extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: SparkDeviceIdWasIdentified if a.deviceInfo.remoteAddress.id == remoteAddressId => Some(a)
    case _ => None
  }
}