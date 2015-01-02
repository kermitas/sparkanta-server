package as.sparkanta.actor2.message.serializer

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor2.message.serializer.Serializer.Serialize

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class SerializerClassifier(broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: Serialize => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case _ => None
  }
}