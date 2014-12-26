/*
package as.sparkanta.actor.message.incoming

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import as.sparkanta.device.message.Disconnect
//import as.sparkanta.gateway.message.DataFromDevice
import as.sparkanta.server.message.MessageToDevice

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class IncomingDataListenerClassifier(remoteAddressId: Long) extends Classifier {
  override def map(message: Any, sender: ActorRef) = message match {
    //case a: DataFromDevice if a.deviceInfo.remoteAddress.id == remoteAddressId => Some(a)
    case a: MessageToDevice if a.remoteAddressId == remoteAddressId && a.messageToDevice.isInstanceOf[Disconnect] => Some(a.messageToDevice.asInstanceOf[Disconnect])
    case _ => None
  }
}
*/ 