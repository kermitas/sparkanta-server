package as.sparkanta.actor.message

import akka.actor.{ ActorLogging, Actor, ActorRef }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.message.{ NewMessageDataFromDevice, CouldNotDeserializeDataFromDevice, NewMessageFromDevice }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.device.message.deserialize.Deserializers

class Deserializer(amaConfig: AmaConfig) extends Actor with ActorLogging {

  protected val deserializers = new Deserializers

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new DeserializerClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {
    case nmdfd: NewMessageDataFromDevice => newMessageDataFromDevice(nmdfd, sender())
    case message                         => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def newMessageDataFromDevice(nmdfd: NewMessageDataFromDevice, couldNotDeserializeResponseListener: ActorRef): Unit = try {
    val messageFromDevice = deserializers.deserialize(nmdfd.dataFromDevice)
    amaConfig.broadcaster ! new NewMessageFromDevice(nmdfd.networkDeviceInfo, messageFromDevice)
  } catch {
    case e: Exception => {
      val cnd = new CouldNotDeserializeDataFromDevice(nmdfd.networkDeviceInfo, e)
      couldNotDeserializeResponseListener ! cnd
      amaConfig.broadcaster ! cnd
    }
  }
}