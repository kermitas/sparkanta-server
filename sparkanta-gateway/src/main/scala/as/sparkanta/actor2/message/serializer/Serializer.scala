package as.sparkanta.actor2.message.serializer

import as.ama.util.FromBroadcaster
import scala.util.Try
import akka.actor.{ ActorRef, ActorLogging, Actor }
import akka.util.ReplyOn1Impl
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.device.message.todevice.MessageToDevice
import as.sparkanta.device.AckType
import as.sparkanta.device.message.serialize.Serializers

object Serializer {
  trait Message extends Serializable
  trait IncomingMessage extends Message
  trait OutgoingMessage extends Message

  class Serialize(val messageToDevice: MessageToDevice, val ack: AckType) extends IncomingMessage
  class SerializationResult(val serializedMessageToDevice: Try[Array[Byte]], serialize: Serialize, serializeSender: ActorRef) extends ReplyOn1Impl[Serialize](serialize, serializeSender) with OutgoingMessage
  class SerializeFromBroadcaster(serialize: Serialize) extends FromBroadcaster[Serialize](serialize) with IncomingMessage
}

class Serializer(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import Serializer._

  protected val serializers = new Serializers

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new SerializerClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {

    case s: Serialize => serializeAndSendResponse(s, sender, false)
    case message      => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def serializeAndSendResponse(serialize: Serialize, serializeSender: ActorRef, publishReplyOnBroadcaster: Boolean): Unit = try {
    val serializationResult = performSerialization(serialize, serializeSender)
    sendResponse(serializationResult, serializeSender, publishReplyOnBroadcaster)
  } catch {
    case e: Exception => log.error("Problem during serialization.", e)
  }

  protected def performSerialization(serialize: Serialize, serializeSender: ActorRef): SerializationResult = {
    val serializedMessageToDevice = Try { serializers.serialize(serialize.messageToDevice, serialize.ack) }
    new SerializationResult(serializedMessageToDevice, serialize, serializeSender)
  }

  protected def sendResponse(serializationResult: SerializationResult, responseListener: ActorRef, publishReplyOnBroadcaster: Boolean): Unit = {
    responseListener ! serializationResult
    if (publishReplyOnBroadcaster) amaConfig.broadcaster ! serializationResult
  }
}
