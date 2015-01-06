package as.sparkanta.actor.device1.blinker

import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.gateway.Device

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class BlinkerWorkerClassifier(id: Long) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {
    case a: Device.IdentifiedDeviceDown if a.deviceInfo.connectionInfo.remote.id == id => Some(messageWithSender)
    case _ => None
  }
}