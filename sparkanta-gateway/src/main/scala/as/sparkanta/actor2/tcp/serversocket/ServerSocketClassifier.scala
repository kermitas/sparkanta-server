package as.sparkanta.actor2.tcp.serversocket

import akka.actor.ActorRef
import as.akka.broadcaster.{ MessageWithSender, Classifier }
import as.sparkanta.actor2.tcp.serversocket.ServerSocket.{ ListenAt, StopListeningAt }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class ServerSocketClassifier(broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: ListenAt => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case a: StopListeningAt => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case _ => None
  }
}