package as.sparkanta.actor2.message

import akka.actor.{ ActorRef, ActorRefFactory, Props }
import akka.util.ForwardToMany
import as.akka.broadcaster.{ MessageWithSender, Classifier }
import as.sparkanta.actor2.message.MessageDataAccumulator.{ AccumulateMessageData, ClearData }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class MessageDataAccumulatorClassifier(actorRefFactory: ActorRefFactory, broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: AccumulateMessageData => {
      val props = Props(new ForwardToMany(true, messageWithSender.messageSender, broadcaster))
      val newSender = actorRefFactory.actorOf(props)
      Some(messageWithSender.copy(messageSender = newSender))
    }

    case a: ClearData => Some(messageWithSender)
    case _            => None
  }
}