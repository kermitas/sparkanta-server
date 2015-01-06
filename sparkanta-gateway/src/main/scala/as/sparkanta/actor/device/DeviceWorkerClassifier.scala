package as.sparkanta.actor.device

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.gateway.{ Device => DeviceSpec }
import as.sparkanta.device.message.fromdevice.DeviceIdentification

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DeviceWorkerClassifier(id: Long, broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: DeviceSpec.NewMessage if a.deviceInfo.connectionInfo.remote.id == id && a.messageFromDevice.isInstanceOf[DeviceIdentification] =>
      Some(new MessageWithSender(a.messageFromDevice, messageWithSender.messageSender))

    case a: DeviceSpec.Stop if a.optionalId.isEmpty || a.optionalId.get == id => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case _ => None
  }
}