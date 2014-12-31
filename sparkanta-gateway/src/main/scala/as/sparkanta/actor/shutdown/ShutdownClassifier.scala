package as.sparkanta.actor.shutdown

import as.akka.broadcaster.{ MessageWithSender, Classifier }
import as.ama.addon.inputstream.InputStreamText

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class ShutdownClassifier extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {
    case a: InputStreamText => Some(messageWithSender)
    case _                  => None
  }
}