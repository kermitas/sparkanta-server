package as.sparkanta.actor.device1.inactivity

import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.gateway.Device

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class InactivityMonitorClassifier extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {
    case a: Device.IdentifiedDeviceUp   => Some(messageWithSender)
    case a: Device.IdentifiedDeviceDown => Some(messageWithSender)
    case _                              => None
  }
}