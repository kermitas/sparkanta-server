package as.sparkanta.actor2.message.deserializer

import akka.actor.{ Props, ActorRef, ActorRefFactory }
import akka.util.ForwardToMany
import as.akka.broadcaster.{ MessageWithSender, Classifier }
import as.sparkanta.actor2.message.deserializer.Deserializer.Deserialize

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DeserializerClassifier(actorRefFactory: ActorRefFactory, broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: Deserialize => {
      val props = Props(new ForwardToMany(true, messageWithSender.messageSender, broadcaster))
      val newSender = actorRefFactory.actorOf(props)
      Some(messageWithSender.copy(messageSender = newSender))
    }

    case _ => None
  }
}