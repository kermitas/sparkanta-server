package as.sparkanta.actor.device

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor.message.deserializer.Deserializer
import as.sparkanta.actor.message.serializer.Serializer
import as.sparkanta.gateway.{ Device => DeviceSpec }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DeviceWorkerClassifier(id: Long, broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: Deserializer.DeserializationSuccessResult => Some(messageWithSender)

    case a: Serializer.SerializationSuccessResult if a.request1.message.isInstanceOf[SerializeWithSendMessage] && a.request1.message.asInstanceOf[SerializeWithSendMessage].sendMessage.id == id => Some(messageWithSender)

    case a: DeviceSpec.DisconnectDevice if a.id == id => Some(messageWithSender)

    case DeviceSpec.DisconnectAllDevices => Some(messageWithSender)

    case _ => None
  }
}