/*
package as.sparkanta.actor.restforwarder

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.message.{ NewMessageFromDevice, ForwardToRestServer }
import as.sparkanta.device.message.fromdevice.DoNotForwardToRestServer

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class RestForwarderClassifier extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {

    case ftrs: ForwardToRestServer => ftrs match {
      case mfd: NewMessageFromDevice => if (mfd.messageFromDevice.isInstanceOf[DoNotForwardToRestServer]) None else Some(mfd)
      case _                         => Some(ftrs)
    }

    case _ => None
  }
}
*/ 