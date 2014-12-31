package as.sparkanta.actor2.inactivity

import akka.actor.{ ActorRef, Props, ActorRefFactory }
import as.akka.broadcaster.{ Classifier, MessageWithSender }
import as.sparkanta.actor2.inactivity.InactivityMonitor.{ StartInactivityMonitor, Active, StopInactivityMonitor }
import akka.util.ForwardToMany

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class InactivityMonitorClassifier(actorRefFactory: ActorRefFactory, broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: StartInactivityMonitor => {
      val props = Props(new ForwardToMany(true, messageWithSender.messageSender, broadcaster))
      val newSender = actorRefFactory.actorOf(props)
      Some(messageWithSender.copy(messageSender = newSender))
    }

    case a: Active                => Some(messageWithSender)
    case a: StopInactivityMonitor => Some(messageWithSender)
    case _                        => None
  }
}