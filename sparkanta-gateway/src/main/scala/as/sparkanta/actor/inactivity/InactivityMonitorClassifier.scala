package as.sparkanta.actor.inactivity

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor.inactivity.InactivityMonitor.{ StartInactivityMonitor, Active, StopInactivityMonitor }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class InactivityMonitorClassifier(broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: StartInactivityMonitor => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case a: Active                => Some(messageWithSender)
    case a: StopInactivityMonitor => Some(messageWithSender)
    case _                        => None
  }
}