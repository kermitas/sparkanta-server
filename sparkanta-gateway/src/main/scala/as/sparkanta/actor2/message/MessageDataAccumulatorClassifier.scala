package as.sparkanta.actor2.message

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor2.message.MessageDataAccumulator.{ AccumulateMessageData, ClearData }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class MessageDataAccumulatorClassifier(broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: AccumulateMessageData => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case a: ClearData => Some(messageWithSender)
    case _            => None
  }
}