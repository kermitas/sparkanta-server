package as.sparkanta.actor2.message.deserializer

import scala.util.Try
import akka.actor.{ ActorRef, ActorLogging, Actor }
import akka.util.ReplyOn1Impl
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.device.message.fromdevice.MessageFormDevice
import as.sparkanta.device.message.deserialize.Deserializers

object Deserializer {
  trait Message extends Serializable
  trait IncomingMessage extends Message
  trait OutgoingMessage extends Message

  class Deserialize(val serializedMessageFromDevice: Array[Byte]) extends IncomingMessage
  class DeserializationResult(val deserializedMessageFromDevice: Try[MessageFormDevice], deserialize: Deserialize, serializeSender: ActorRef) extends ReplyOn1Impl[Deserialize](deserialize, serializeSender) with OutgoingMessage
}

class Deserializer(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import Deserializer._

  protected val deserializers = new Deserializers

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new DeserializerClassifier(context, amaConfig.broadcaster))
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
    deserializeSender ! deserializationResult
  } catch {
    case e: Exception => log.error("Problem during deserialization.", e)
  }

  protected def performDeserialization(deserialize: Deserialize, deserializeSender: ActorRef): DeserializationResult = {
    val deserializedMessageFromDevice = Try { deserializers.deserialize(deserialize.serializedMessageFromDevice) }
    new DeserializationResult(deserializedMessageFromDevice, deserialize, deserializeSender)
  }

}
