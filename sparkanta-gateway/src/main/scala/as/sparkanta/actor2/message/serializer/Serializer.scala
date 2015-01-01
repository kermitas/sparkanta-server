package as.sparkanta.actor2.message.serializer

import scala.util.{ Try, Success, Failure }
import akka.actor.{ ActorRef, ActorLogging, Actor }
import as.akka.broadcaster.{ MessageWithSender, Broadcaster }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.device.message.todevice.MessageToDevice
import as.sparkanta.device.AckType
import as.sparkanta.device.message.serialize.Serializers
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message }

object Serializer {
  class Serialize(val messageToDevice: MessageToDevice, val ack: AckType) extends IncomingReplyableMessage
  abstract class SerializationResult(val serializedMessageToDevice: Try[Array[Byte]], serialize: Serialize, serializeSender: ActorRef) extends OutgoingReplyOn1Message(new MessageWithSender(serialize, serializeSender))
  class SerializationSuccessResult(serializedMessageToDevice: Array[Byte], serialize: Serialize, serializeSender: ActorRef) extends SerializationResult(Success(serializedMessageToDevice), serialize, serializeSender)
  class SerializationErrorResult(exception: Exception, serialize: Serialize, serializeSender: ActorRef) extends SerializationResult(Failure(exception), serialize, serializeSender)
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

  protected def performSerialization(serialize: Serialize, serializeSender: ActorRef): SerializationResult = try {
    val serializedMessageToDevice = serializers.serialize(serialize.messageToDevice, serialize.ack)
    new SerializationSuccessResult(serializedMessageToDevice, serialize, serializeSender)
  } catch {
    case e: Exception => new SerializationErrorResult(e, serialize, serializeSender)
  }
}
