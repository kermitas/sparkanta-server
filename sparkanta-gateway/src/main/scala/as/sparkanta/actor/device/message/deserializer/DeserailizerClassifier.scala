package as.sparkanta.actor.device.message.deserializer

import as.akka.broadcaster.Classifier
import akka.util.MessageWithSender
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.gateway.Device

/**
 * This classifier will be used by broadcaster to test if we are interested (or not)
 * in this message.
 */
class DeserailizerClassifier(id: Long) extends Classifier {
  override def map(messageWithSender: MessageWithSender[Any]) = messageWithSender.message match {
    case a: Socket.NewData if a.request1.message.connectionInfo.remote.id == id => Some(messageWithSender)
    case a: Device.StartSuccessResult if a.request1.message.connectionInfo.remote.id == id => Some(messageWithSender)
    case a: Device.StartErrorResult if a.request1.message.connectionInfo.remote.id == id => Some(messageWithSender)
    case a: Device.Stopped if a.request1.message.connectionInfo.remote.id == id => Some(messageWithSender)
    case a: Device.IdentifiedDeviceUp if a.request1.message.connectionInfo.remote.id == id => Some(messageWithSender)
    case _ => None
  }
}