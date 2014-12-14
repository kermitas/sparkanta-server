package as.sparkanta.actor.restforwarder

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.gateway.message.{ MessageFromDevice, ForwardToRestServer }
import as.sparkanta.device.message.DoNotForwardToRestServer
import scala.net.IdentifiedInetSocketAddress

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class RestForwarderClassifier(localAddress: IdentifiedInetSocketAddress) extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {

    case ftrs: ForwardToRestServer if ftrs.deviceInfo.localAddress.id == localAddress.id => ftrs match {
      case mfd: MessageFromDevice => if (mfd.messageFromDevice.isInstanceOf[DoNotForwardToRestServer]) None else Some(mfd)
      case _                      => Some(ftrs)
    }

    case _ => None
  }
}