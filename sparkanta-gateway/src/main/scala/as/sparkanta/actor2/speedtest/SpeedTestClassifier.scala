package as.sparkanta.actor2.speedtest

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor2.speedtest.SpeedTest.StartSpeedTest

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class SpeedTestClassifier(broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: StartSpeedTest => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case _ => None
  }
}