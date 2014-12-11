package as.sparkanta.actor.devicesdb

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.gateway.message.{ NewIncomingConnection, ConnectionClosed, SoftwareVersionWasIdentified, SparkDeviceIdWasIdentified }
import as.sparkanta.gateway.message.GetCurrentDevices

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DevicesDbClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: NewIncomingConnection        => Some(a)
    case a: SoftwareVersionWasIdentified => Some(a)
    case a: SparkDeviceIdWasIdentified   => Some(a)
    case a: ConnectionClosed             => Some(a)
    case a: GetCurrentDevices            => Some(a)
    case _                               => None
  }
}