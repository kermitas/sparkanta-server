package as.sparkanta.actor.device.message.deserializer

import akka.actor.{ ActorRef, ActorLogging, Actor, ActorRefFactory, Props }
import akka.util.ByteString
import as.akka.broadcaster.Broadcaster
import as.sparkanta.actor.message.MessageDataAccumulator
import as.sparkanta.device.DeviceInfo
import as.sparkanta.gateway.Device
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

class Deserializer(var deviceInfo: DeviceInfo, broadcaster: ActorRef, var deviceActor: ActorRef) extends Actor with ActorLogging {

  protected val deserializers = new Deserializers
  protected var messageAccumulatorStarted = false

  override def receive = {
    case a: Socket.NewData => newData(a)
    case a: MessageDataAccumulator.MessageDataAccumulationSuccessResult => messageDataAccumulationSuccess(a)
    case a: MessageDataAccumulator.MessageDataAccumulationErrorResult => messageDataAccumulationError(a)
    case a: MessageDataAccumulator.StartDataAccumulationSuccessResult => // do nothing
    case a: MessageDataAccumulator.StartDataAccumulationErrorResult => startDataAccumulationError(a)
    case a: Device.StartSuccessResult => deviceStartSuccess(sender)
    case a: Device.StartErrorResult => deviceStartError(a)
    case a: Device.Stopped => deviceStopped(a)
    case a: Device.IdentifiedDeviceUp => identifiedDeviceUp(a)

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def startDataAccumulationError(startDataAccumulationErrorResult: MessageDataAccumulator.StartDataAccumulationErrorResult): Unit =
    startDataAccumulationError(startDataAccumulationErrorResult.request1.message.id, startDataAccumulationErrorResult.exception)

  protected def startDataAccumulationError(id: Long, exception: Exception): Unit =
    stop(new Exception("Problem while starting message data accumulator.", exception))

  protected def deviceStartSuccess(startSuccessResultSender: ActorRef) = {
    deviceActor = startSuccessResultSender
    messageAccumulatorStarted = true
    broadcaster ! new MessageDataAccumulator.StartDataAccumulation(deviceInfo.connectionInfo.remote.id)
  }

  protected def deviceStartError(startErrorResult: Device.StartErrorResult): Unit = stop

  protected def deviceStopped(stopped: Device.Stopped): Unit = stop

  protected def identifiedDeviceUp(identifiedDeviceUpMessage: Device.IdentifiedDeviceUp): Unit =
    deviceInfo = identifiedDeviceUpMessage.deviceInfo

  protected def newData(newDataMessage: Socket.NewData): Unit =
    newData(newDataMessage.request1.message.connectionInfo.remote.id, newDataMessage.data)

  protected def newData(id: Long, data: ByteString): Unit =
    broadcaster ! new MessageDataAccumulator.AccumulateMessageData(id, data)

  protected def messageDataAccumulationError(messageDataAccumulationErrorResult: MessageDataAccumulator.MessageDataAccumulationErrorResult): Unit =
    messageDataAccumulationError(messageDataAccumulationErrorResult.request1.message.id, messageDataAccumulationErrorResult.exception)

  protected def messageDataAccumulationError(id: Long, exception: Exception): Unit =
    stop(new Exception("Problem during message data accumulation.", exception))

  protected def messageDataAccumulationSuccess(messageDataAccumulationSuccessResult: MessageDataAccumulator.MessageDataAccumulationSuccessResult): Unit = try {
    messageDataAccumulationSuccessResult.messageData.foreach { serializedMessageFromDevice =>
      val deserializedMessageFromDevice = deserializers.deserialize(serializedMessageFromDevice)
      broadcaster.tell(new Device.NewMessage(deviceInfo, deserializedMessageFromDevice), deviceActor)
    }
  } catch {
    case e: Exception => stop(new Exception("Problem during message deserialization.", e))
  }

  protected def stop: Unit = stop(None)

  protected def stop(exception: Exception): Unit = stop(Some(exception))

  protected def stop(exception: Option[Exception]): Unit = {
    exception.map { e =>
      log.error(e, "Stopping because of problem.")
      broadcaster ! new Device.StopDevice(deviceInfo.connectionInfo.remote.id, e)
    }
    if (messageAccumulatorStarted) broadcaster ! new MessageDataAccumulator.StopDataAccumulation(deviceInfo.connectionInfo.remote.id)
    context.stop(self)
  }
}
