package akka.util

import akka.actor.ActorRef
import as.akka.broadcaster.MessageWithSender

trait ActorMessage extends Serializable

trait IncomingMessage extends ActorMessage

class IncomingReplyableMessage(var replyAlsoOn: Option[Seq[ActorRef]] = None) extends ActorMessage

trait InternalMessage extends IncomingMessage

trait OutgoingMessage extends ActorMessage

class OutgoingReplyOn1Message[T <: IncomingReplyableMessage](request1: MessageWithSender[T]) extends ReplyOn1Impl(request1) with OutgoingMessage {
  def reply(sender: ActorRef): Unit = {
    request1.messageSender.tell(this, sender)
    request1.message.replyAlsoOn.map(_.foreach(_.tell(this, sender)))
  }
}
class OutgoingReplyOn2Message[T <: IncomingReplyableMessage, E](request1: MessageWithSender[T], request2: MessageWithSender[E]) extends ReplyOn2Impl(request1, request2) with OutgoingMessage {
  def reply(sender: ActorRef): Unit = {
    request1.messageSender.tell(this, sender)
    request1.message.replyAlsoOn.map(_.foreach(_.tell(this, sender)))
  }
}