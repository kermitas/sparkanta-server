package as.sparkanta.actor.device.message.deserializer

import akka.actor.{ ActorRef, ActorLogging, Actor, ActorRefFactory, Props }
import akka.util.ByteString
import as.akka.broadcaster.Broadcaster
import as.sparkanta.device.DeviceInfo
import as.sparkanta.gateway.Device
import scala.collection.mutable.ListBuffer
import scala.net.IdentifiedConnectionInfo
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.device.message.deserialize.Deserializers

object Deserializer {

  class SemiInitializedDeviceInfo(connectionInfo: IdentifiedConnectionInfo) extends DeviceInfo(connectionInfo, null, None) {
    override def toString = s"${getClass.getSimpleName}(connectionInfo=$connectionInfo)"
  }

  def startActor(actorRefFactory: ActorRefFactory, connectionInfo: IdentifiedConnectionInfo, broadcaster: ActorRef, deviceActor: ActorRef): ActorRef = {
    val props = Props(new Deserializer(new SemiInitializedDeviceInfo(connectionInfo), broadcaster, deviceActor))
    val actor = actorRefFactory.actorOf(props, name = classOf[Deserializer].getSimpleName + "-" + connectionInfo.remote.id)
    broadcaster ! new Broadcaster.Register(actor, new DeserailizerClassifier(connectionInfo.remote.id))
    actor
  }
}

class Deserializer(
  var deviceInfo: DeviceInfo,
  broadcaster:    ActorRef,
  deviceActor:    ActorRef
) extends Actor with ActorLogging {

  protected val deserializers = new Deserializers
  protected var buffer = ByteString.empty

  override def receive = {
    case a: Socket.NewData            => newData(a)
    case a: Device.IdentifiedDeviceUp => identifiedDeviceUp(a)

    case message                      => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def identifiedDeviceUp(identifiedDeviceUpMessage: Device.IdentifiedDeviceUp): Unit =
    deviceInfo = identifiedDeviceUpMessage.deviceInfo

  protected def newData(newDataMessage: Socket.NewData): Unit = {
    buffer = buffer ++ newDataMessage.data

    log.debug(s"There are ${buffer.size} accumulated bytes after accumulating new ${newDataMessage.data.size} bytes for id ${deviceInfo.connectionInfo.remote.id}.")

    val accumulatedMessageData = analyzeBuffer

    accumulatedMessageData.foreach { serializedMessageFromDevice =>
      val deserializedMessageFromDevice = deserializers.deserialize(serializedMessageFromDevice)
      broadcaster.tell(new Device.NewMessage(deviceInfo, deserializedMessageFromDevice), deviceActor)
    }
  }

  protected def analyzeBuffer: Seq[Array[Byte]] = {

    var bufferSize = buffer.size

    if (bufferSize > 0) {
      val result = new ListBuffer[Array[Byte]]

      log.debug(s"New message (for id ${deviceInfo.connectionInfo.remote.id}) will have ${buffer(0)} bytes (${buffer(0) + 1 - bufferSize} bytes needs to be collected to have full message).")

      while (bufferSize > 0 && bufferSize >= buffer(0) + 1) {
        val messageLength = buffer(0)

        log.debug(s"Got all $messageLength bytes for incoming message for id ${deviceInfo.connectionInfo.remote.id}.")

        val messageDataAndRest = buffer.drop(1).splitAt(messageLength)

        result += messageDataAndRest._1.toArray
        buffer = messageDataAndRest._2

        bufferSize = buffer.size
      }

      result
    } else {
      Seq.empty
    }
  }
}
