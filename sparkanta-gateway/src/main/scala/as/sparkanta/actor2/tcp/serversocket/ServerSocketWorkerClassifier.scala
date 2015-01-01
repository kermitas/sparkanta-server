/*
package as.sparkanta.actor2.tcp.serversocket

import akka.actor.ActorRef
import as.akka.broadcaster.{ MessageWithSender, Classifier }
import as.sparkanta.actor2.tcp.serversocket.ServerSocket.{ ListenAt, StopListeningAt, ListeningStarted, ListeningStopped }

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class ServerSocketWorkerClassifier(id: Long, broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: ListenAt if a.listenAddress.id == id => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case a: StopListeningAt if a.id == id => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case _ => None
  }
}
*/ 