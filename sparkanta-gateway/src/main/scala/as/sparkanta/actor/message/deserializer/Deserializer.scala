/*
package as.sparkanta.actor.message.deserializer

import scala.util.{ Try, Success, Failure }
import akka.actor.{ ActorRef, ActorLogging, Actor }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.device.message.fromdevice.MessageFromDevice
//import as.sparkanta.device.message.deserialize.Deserializers
import akka.util.{ IncomingReplyableMessage, OutgoingReplyOn1Message }
import as.ama.addon.lifecycle.ShutdownSystem
import as.sparkanta.device.message.deserialize.{ Deserializer => DeserializerClass }

object Deserializer {
  class Deserialize(val serializedMessageFromDevice: Array[Byte], val deserializer: DeserializerClass[MessageFromDevice]) extends IncomingReplyableMessage
  abstract class DeserializationResult(val tryDeserializedMessageFromDevice: Try[MessageFromDevice], deserialize: Deserialize, serializeSender: ActorRef) extends OutgoingReplyOn1Message(deserialize, serializeSender)
  class DeserializationSuccessResult(val deserializedMessageFromDevice: MessageFromDevice, deserialize: Deserialize, serializeSender: ActorRef) extends DeserializationResult(Success(deserializedMessageFromDevice), deserialize, serializeSender)
  class DeserializationErrorResult(val exception: Exception, deserialize: Deserialize, serializeSender: ActorRef) extends DeserializationResult(Failure(exception), deserialize, serializeSender)
}

class Deserializer(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import Deserializer._

  //protected val deserializers = new Deserializers

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new DeserializerClassifier(amaConfig.broadcaster))
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${getClass.getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: Deserialize => deserializeAndSendResponse(a, sender)
    case message        => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def deserializeAndSendResponse(deserialize: Deserialize, deserializeSender: ActorRef): Unit = try {
    val deserializationResult = performDeserialization(deserialize, deserializeSender)
    deserializationResult.reply(self)
  } catch {
    case e: Exception => log.error(e, "Problem during deserialization.")
  }

  protected def performDeserialization(deserialize: Deserialize, deserializeSender: ActorRef): DeserializationResult = try {
    val deserializedMessageFromDevice = deserialize.deserializer.deserialize(deserialize.serializedMessageFromDevice)
    new DeserializationSuccessResult(deserializedMessageFromDevice, deserialize, deserializeSender)
  } catch {
    case e: Exception => new DeserializationErrorResult(e, deserialize, deserializeSender)
  }
}
*/ 