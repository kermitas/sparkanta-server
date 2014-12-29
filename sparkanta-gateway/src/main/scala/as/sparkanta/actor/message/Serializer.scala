/*
package as.sparkanta.actor.message

import akka.actor.{ ActorLogging, Actor, ActorRef }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.message.{ SendDataToDevice, SendMessageToDevice, SendMessageToDeviceResult, SendMessageToDeviceErrorResult, DeviceAck }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.device.message.serialize.Serializers
import as.sparkanta.message.AckType

class Serializer(amaConfig: AmaConfig) extends Actor with ActorLogging {

  protected val serializers = new Serializers

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new SerializerClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {
    case smtd: SendMessageToDevice        => sendMessageToDevice(smtd, sender())

    case smtdr: SendMessageToDeviceResult => sendResponse(smtdr)

    case message                          => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def sendMessageToDevice(sendMessageToDevice: SendMessageToDevice, sendMessageToDeviceResponseListener: ActorRef): Unit = try {

    val dataToDevice = sendMessageToDevice.ack match {
      case da: DeviceAck => serializers.serialize(sendMessageToDevice.messageToDevice, da.deviceAck)
      case _             => serializers.serialize(sendMessageToDevice.messageToDevice)
    }

    amaConfig.broadcaster ! new SendDataToDeviceWithSendMessageToDevice(sendMessageToDevice.remoteAddressId, dataToDevice, sendMessageToDevice.ack, sendMessageToDevice, sendMessageToDeviceResponseListener)

  } catch {
    case e: Exception => {
      val sendMessageToDeviceErrorResult = new SendMessageToDeviceErrorResult(sendMessageToDevice, e)
      sendMessageToDeviceResponseListener ! sendMessageToDeviceErrorResult
      amaConfig.broadcaster ! sendMessageToDeviceErrorResult
    }
  }

  protected def sendResponse(smtdr: SendMessageToDeviceResult): Unit = {
    val sdtdwsmtd = smtdr.sendMessageToDevice.asInstanceOf[SendDataToDeviceWithSendMessageToDevice]
    sendResponse(sdtdwsmtd, smtdr.exception)
  }

  protected def sendResponse(sdtdwsmtd: SendDataToDeviceWithSendMessageToDevice, optionalException: Option[Exception]): Unit = {
    val sendMessageToDeviceResult = new SendMessageToDeviceResult(sdtdwsmtd.sendMessageToDevice, optionalException)
    sdtdwsmtd.sendMessageToDeviceResponseListener ! sendMessageToDeviceResult
    amaConfig.broadcaster ! sendMessageToDeviceResult
  }
}

class SendDataToDeviceWithSendMessageToDevice(
  remoteAddressId:                         Long,
  dataToDevice:                            Array[Byte],
  ack:                                     AckType,
  val sendMessageToDevice:                 SendMessageToDevice,
  val sendMessageToDeviceResponseListener: ActorRef
) extends SendDataToDevice(remoteAddressId, dataToDevice, ack)
*/ 