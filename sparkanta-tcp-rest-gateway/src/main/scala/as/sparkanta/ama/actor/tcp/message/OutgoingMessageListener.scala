package as.sparkanta.ama.actor.tcp.message

import akka.actor.{ Actor, ActorLogging, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.device.message.serialize.Serializer
import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker, MessageLengthHeader }
import as.sparkanta.gateway.message.{ DataToDevice, MessageToDevice }

class OutgoingMessageListener(
  val amaConfig:           AmaConfig,
  val runtimeId:           Long,
  val serializer:          Serializer[MessageToDeviceMarker],
  val messageLengthHeader: MessageLengthHeader
) extends Actor with ActorLogging {

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      log.error(t, "Terminating because once of child actors failed.")
      context.stop(self)
      SupervisorStrategy.Escalate
    }
  }

  override def preStart(): Unit = {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new OutgoingMessageListenerClassifier(runtimeId))
  }

  override def receive = {
    case mtd: MessageToDevice => serializeMessageToDevice(mtd.messageToDevice)
    case message              => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def serializeMessageToDevice(messageToDevice: MessageToDeviceMarker): Unit = {
    val messageToDeviceAsBytes = serializer.serialize(messageToDevice)
    val dataToDevice = messageLengthHeader.prepareMessageToGo(messageToDeviceAsBytes)
    amaConfig.broadcaster ! new DataToDevice(runtimeId, dataToDevice)
  }
}
