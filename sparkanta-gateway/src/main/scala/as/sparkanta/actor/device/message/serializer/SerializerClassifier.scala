package as.sparkanta.actor.device.message.serializer

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.gateway.Device
import as.sparkanta.device.message.fromdevice.Ack

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class SerializerClassifier(id: Long, broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: Device.SendMessage if a.id == id => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case a: Device.NewMessage if a.deviceInfo.connectionInfo.remote.id == id && a.messageFromDevice.isInstanceOf[Ack] =>
      Some(new MessageWithSender(a.messageFromDevice, messageWithSender.messageSender))

    case _ => None
  }
}