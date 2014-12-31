package as.sparkanta.actor2.message.serializer

import scala.util.Try
import akka.actor.{ ActorRef, ActorLogging, Actor }
//import akka.util.ReplyOn1Impl
import as.akka.broadcaster.{ MessageWithSender, Broadcaster }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.device.message.todevice.MessageToDevice
import as.sparkanta.device.AckType
import as.sparkanta.device.message.serialize.Serializers
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message }

object Serializer {
  /*trait Message extends Serializable
  trait IncomingMessage extends Message
  trait OutgoingMessage extends Message*/

  class Serialize(val messageToDevice: MessageToDevice, val ack: AckType) extends IncomingReplyableMessage
  class SerializationResult(val serializedMessageToDevice: Try[Array[Byte]], serialize: Serialize, serializeSender: ActorRef) extends OutgoingReplyOn1Message(new MessageWithSender(serialize, serializeSender))
}

class Serializer(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import Serializer._

  protected val serializers = new Serializers

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new SerializerClassifier(amaConfig.broadcaster))
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {
    case a: Serialize => serializeAndSendResponse(a, sender)
    case message      => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def serializeAndSendResponse(serialize: Serialize, serializeSender: ActorRef): Unit = try {
    val serializationResult = performSerialization(serialize, serializeSender)
    serializationResult.reply(self)
  } catch {
    case e: Exception => log.error("Problem during serialization.", e)
  }

  protected def performSerialization(serialize: Serialize, serializeSender: ActorRef): SerializationResult = {
    val serializedMessageToDevice = Try { serializers.serialize(serialize.messageToDevice, serialize.ack) }
    new SerializationResult(serializedMessageToDevice, serialize, serializeSender)
  }
}
