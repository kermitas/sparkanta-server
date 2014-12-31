package as.sparkanta.actor2.message.deserializer

import akka.actor.ActorRef
//import akka.util.ForwardToMany
import as.akka.broadcaster.{ MessageWithSender, Classifier }
import as.sparkanta.actor2.message.deserializer.Deserializer.Deserialize

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DeserializerClassifier(broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: Deserialize => {
      //val props = Props(new ForwardToMany(true, messageWithSender.messageSender, broadcaster))
      //val newSender = actorRefFactory.actorOf(props)
      //Some(messageWithSender.copy(messageSender = newSender))

      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case _ => None
  }
}