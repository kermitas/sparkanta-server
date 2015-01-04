/*
package as.sparkanta.actor.device

import akka.actor.ActorRef
import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.gateway.{ Device => DeviceSpec }
import as.sparkanta.device.message.fromdevice.Ack

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DeviceMessageSenderWorkerClassifier(id: Long, broadcaster: ActorRef) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {

    case a: DeviceSpec.SendMessage => {
      a.replyAlsoOn = Some(Seq(broadcaster))
      Some(messageWithSender)
    }

    case a: DeviceSpec.NewMessage if a.deviceInfo.connectionInfo.remote.id == id && a.messageFromDevice.isInstanceOf[Ack] =>
      Some(new MessageWithSender(a.messageFromDevice, messageWithSender.messageSender))

    case a: Socket.ListeningStopped if a.request1.message.connectionInfo.remote.id == id => Some(messageWithSender)

    case _ => None
  }
}
*/ 