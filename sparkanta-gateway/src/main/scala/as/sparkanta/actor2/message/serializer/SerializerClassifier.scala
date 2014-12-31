package as.sparkanta.actor2.message.serializer

import akka.actor.{ ActorRef, ActorRefFactory, Props }
import akka.util.ForwardToMany
import as.akka.broadcaster.{ MessageWithSender, Classifier }
import as.sparkanta.actor2.message.serializer.Serializer.Serialize

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class SerializerClassifier(actorRefFactory: ActorRefFactory, broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: Serialize => {
      val props = Props(new ForwardToMany(true, messageWithSender.messageSender, broadcaster))
      val newSender = actorRefFactory.actorOf(props)
      Some(messageWithSender.copy(messageSender = newSender))
    }

    case _ => None
  }
}