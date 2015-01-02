package as.sparkanta.actor2.tcp.socket

import akka.actor.ActorRef
import as.akka.broadcaster.{ MessageWithSender, Classifier }
import as.sparkanta.actor2.tcp.socket.Socket.{ ListenAt, StopListeningAt, SendData }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class SocketClassifier(broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: ListenAt => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case a: StopListeningAt => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case a: SendData => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case _ => None
  }
}