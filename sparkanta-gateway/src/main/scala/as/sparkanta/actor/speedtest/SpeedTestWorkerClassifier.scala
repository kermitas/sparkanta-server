package as.sparkanta.actor.speedtest

import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.device.message.fromdevice.Pong
import as.sparkanta.gateway.Device

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class SpeedTestWorkerClassifier(id: Long) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {
    case a: Device.NewMessage if a.deviceInfo.connectionInfo.remote.id == id && a.messageFromDevice.isInstanceOf[Pong] => Some(messageWithSender)
    case _ => None
  }
}