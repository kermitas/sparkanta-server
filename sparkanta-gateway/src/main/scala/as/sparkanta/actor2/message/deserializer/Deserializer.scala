package as.sparkanta.actor2.message.deserializer

import scala.util.{ Try, Success, Failure }
import akka.actor.{ ActorRef, ActorLogging, Actor }
import as.akka.broadcaster.{ MessageWithSender, Broadcaster }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.device.message.fromdevice.MessageFormDevice
import as.sparkanta.device.message.deserialize.Deserializers
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message }

object Deserializer {
  class Deserialize(val serializedMessageFromDevice: Array[Byte]) extends IncomingReplyableMessage
  abstract class DeserializationResult(val deserializedMessageFromDevice: Try[MessageFormDevice], deserialize: Deserialize, serializeSender: ActorRef) extends OutgoingReplyOn1Message(new MessageWithSender(deserialize, serializeSender))
  class DeserializationSuccessResult(deserializedMessageFromDevice: MessageFormDevice, deserialize: Deserialize, serializeSender: ActorRef) extends DeserializationResult(Success(deserializedMessageFromDevice), deserialize, serializeSender)
  class DeserializationErrorResult(exception: Exception, deserialize: Deserialize, serializeSender: ActorRef) extends DeserializationResult(Failure(exception), deserialize, serializeSender)
}

class Deserializer(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import Deserializer._

  protected val deserializers = new Deserializers

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new DeserializerClassifier(amaConfig.broadcaster))
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {
    case a: Deserialize => deserializeAndSendResponse(a, sender)
    case message        => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def deserializeAndSendResponse(deserialize: Deserialize, deserializeSender: ActorRef): Unit = try {
    val deserializationResult = performDeserialization(deserialize, deserializeSender)
    deserializationResult.reply(self)
  } catch {
    case e: Exception => log.error("Problem during deserialization.", e)
  }

  protected def performDeserialization(deserialize: Deserialize, deserializeSender: ActorRef): DeserializationResult = try {
    val deserializedMessageFromDevice = deserializers.deserialize(deserialize.serializedMessageFromDevice)
    new DeserializationSuccessResult(deserializedMessageFromDevice, deserialize, deserializeSender)
  } catch {
    case e: Exception => new DeserializationErrorResult(e, deserialize, deserializeSender)
  }
}
