package as.sparkanta.actor.device1

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor.tcp.serversocket.ServerSocket
import as.sparkanta.gateway.{ Device => DeviceSpec }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DeviceClassifier(broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: DeviceSpec.Start => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case a: DeviceSpec.Stop => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case _ => None
  }
}