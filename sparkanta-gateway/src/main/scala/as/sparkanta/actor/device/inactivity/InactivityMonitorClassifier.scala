package as.sparkanta.actor.device.inactivity

import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.gateway.Device

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class InactivityMonitorClassifier(id: Long) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {
    case a: Device.NewMessage if a.deviceInfo.connectionInfo.remote.id == id => Some(messageWithSender)
    case _ => None
  }
}