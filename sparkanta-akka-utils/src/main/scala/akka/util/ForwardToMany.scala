/*
package akka.util

import akka.actor.{ ActorLogging, ActorRef, Actor }

class ForwardToMany(replyListeners: Seq[ActorRef], dieAfterFirstForward: Boolean) extends Actor with ActorLogging {

  def this(dieAfterFirstForward: Boolean, replyListeners: ActorRef*) = this(replyListeners, dieAfterFirstForward)

  require(replyListeners.nonEmpty, "Sequence of forwardees can not be empty.")

  override def receive = {

    case message => {
      val s = sender()

      var i = 1
      replyListeners.foreach { forwardee =>
        log.debug(s"Forwarding message $message to $i/${replyListeners.size} listener $forwardee.")
        forwardee.tell(message, s)
        i += 1
      }

      if (dieAfterFirstForward) {
        log.debug(s"Stopping after forwarding $message.")
        context.stop(self)
      }
    }

  }

}
*/ 