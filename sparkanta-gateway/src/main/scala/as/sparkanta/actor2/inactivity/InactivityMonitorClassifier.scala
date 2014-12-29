package as.sparkanta.actor2.inactivity

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.actor2.inactivity.InactivityMonitor.{ StartInactivityMonitor, Active, StopInactivityMonitor, StartInactivityMonitorFromBroadcaster }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class InactivityMonitorClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    case a: StartInactivityMonitor => Some(new StartInactivityMonitorFromBroadcaster(a))
    case a: Active                 => Some(a)
    case a: StopInactivityMonitor  => Some(a)
    case _                         => None
  }
}