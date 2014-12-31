package akka.util

import akka.actor.{ ActorRef, Actor }

class ForwardToMany(replyListeners: Seq[ActorRef], dieAfterFirstForward: Boolean) extends Actor {

  def this(dieAfterFirstForward: Boolean, replyListeners: ActorRef*) = this(replyListeners, dieAfterFirstForward)

  require(replyListeners.nonEmpty, "Sequence of forwardees can not be empty.")

  override def receive = {

    case message => {
      val s = sender()
      replyListeners.foreach(_.tell(message, s))
      if (dieAfterFirstForward) context.stop(self)
    }

  }

}
