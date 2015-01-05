package as.sparkanta.actor.device1.message.deserializer

import akka.actor.{ ActorRef, ActorLogging, Actor }
import akka.util.ByteString
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.ama.addon.lifecycle.ShutdownSystem
import as.sparkanta.actor.tcp.serversocket.ServerSocket
import as.sparkanta.actor.message.MessageDataAccumulator
import as.sparkanta.device.DeviceInfo
import as.sparkanta.gateway.Device
import scala.collection.mutable.Map
import scala.net.IdentifiedConnectionInfo
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.actor.message.deserializer.{ Deserializer => GeneralDeserializer }
import as.sparkanta.device.message.fromdevice.MessageFromDevice

object Deserializer {

  class DeserializeWithId(val id: Long, messageData: Array[Byte]) extends GeneralDeserializer.Deserialize(messageData)

  class SemiInitializedDeviceInfo(connectionInfo: IdentifiedConnectionInfo) extends DeviceInfo(connectionInfo, null, None) {
    override def toString = s"${getClass.getSimpleName}(connectionInfo=$connectionInfo)"
  }

  class Record(var deviceInfo: DeviceInfo, var deviceActor: Option[ActorRef] = None)
}

class Deserializer(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import Deserializer._

  protected val map = Map[Long, Record]()

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new DeserializerClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${getClass.getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: Socket.NewData => newData(a)
    case a: MessageDataAccumulator.MessageDataAccumulationSuccessResult => messageDataAccumulationSuccess(a)
    case a: GeneralDeserializer.DeserializationSuccessResult => deserializationSuccess(a)
    case a: MessageDataAccumulator.MessageDataAccumulationErrorResult => messageDataAccumulationError(a)
    case a: GeneralDeserializer.DeserializationErrorResult => deserializationError(a)
    case a: ServerSocket.NewConnection => newConnection(a)
    case a: MessageDataAccumulator.StartDataAccumulationSuccessResult => // do nothing
    case a: MessageDataAccumulator.StartDataAccumulationErrorResult => startDataAccumulationError(a)
    case a: Device.StartSuccessResult => deviceStartSuccess(a, sender)
    case a: Device.StartErrorResult => deviceStartError(a)
    case a: Device.Stopped => deviceStopped(a)
    case a: Device.IdentifiedDeviceUp => identifiedDeviceUp(a)

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def newConnection(newConnection: ServerSocket.NewConnection): Unit = try {
    if (map.contains(newConnection.connectionInfo.remote.id)) {
      throw new Exception(s"Id ${newConnection.connectionInfo.remote.id} is already registered, could not register new.")
    } else {
      val record = new Record(new SemiInitializedDeviceInfo(newConnection.connectionInfo))
      putToMap(newConnection.connectionInfo.remote.id, record)
      amaConfig.broadcaster ! new MessageDataAccumulator.StartDataAccumulation(newConnection.connectionInfo.remote.id)
    }
  } catch {
    case e: Exception => {
      val exception = new Exception(s"Problem during setup work for device of remote address id ${newConnection.connectionInfo.remote.id}.", e)
      log.error(exception, exception.getMessage)
      amaConfig.broadcaster ! new Device.StopDevice(newConnection.connectionInfo.remote.id, exception)
    }
  }

  protected def startDataAccumulationError(startDataAccumulationErrorResult: MessageDataAccumulator.StartDataAccumulationErrorResult): Unit =
    startDataAccumulationError(startDataAccumulationErrorResult.request1.message.id, startDataAccumulationErrorResult.exception)

  protected def startDataAccumulationError(id: Long, exception: Exception): Unit = {
    removeFromMap(id)
    val e = new Exception("Problem while starting message data accumulator.", exception)
    amaConfig.broadcaster ! new Device.StopDevice(id, e)
  }

  protected def deviceStartSuccess(startSuccessResult: Device.StartSuccessResult, startSuccessResultSender: ActorRef): Unit =
    deviceStartSuccess(startSuccessResult.request1.message.connectionInfo.remote.id, startSuccessResultSender)

  protected def deviceStartSuccess(id: Long, startSuccessResultSender: ActorRef): Unit = map.get(id) match {
    case Some(record) => record.deviceActor = Some(startSuccessResultSender)

    case None => {
      val exception = new Exception(s"Could not find device of remote id $id in internal map.")
      amaConfig.broadcaster ! new Device.StopDevice(id, exception)
    }
  }

  protected def deviceStartError(startErrorResult: Device.StartErrorResult): Unit =
    deviceStartError(startErrorResult.request1.message.connectionInfo.remote.id, startErrorResult.exception)

  protected def deviceStartError(id: Long, exception: Exception): Unit = removeFromMap(id)

  protected def deviceStopped(stopped: Device.Stopped): Unit = deviceStopped(stopped.request1.message.connectionInfo.remote.id)

  protected def deviceStopped(id: Long): Unit = removeFromMap(id)

  protected def identifiedDeviceUp(identifiedDeviceUpMessage: Device.IdentifiedDeviceUp): Unit =
    identifiedDeviceUp(identifiedDeviceUpMessage.deviceInfo)

  protected def identifiedDeviceUp(newDeviceInfo: DeviceInfo): Unit = map.get(newDeviceInfo.connectionInfo.remote.id) match {
    case Some(record) => record.deviceInfo = newDeviceInfo

    case None => {
      val exception = new Exception(s"Could not find device of remote id ${newDeviceInfo.connectionInfo.remote.id} in internal map.")
      amaConfig.broadcaster ! new Device.StopDevice(newDeviceInfo.connectionInfo.remote.id, exception)
    }
  }

  protected def newData(newDataMessage: Socket.NewData): Unit =
    newData(newDataMessage.request1.message.connectionInfo.remote.id, newDataMessage.data)

  protected def newData(id: Long, data: ByteString): Unit =
    amaConfig.broadcaster ! new MessageDataAccumulator.AccumulateMessageData(id, data)

  protected def messageDataAccumulationError(messageDataAccumulationErrorResult: MessageDataAccumulator.MessageDataAccumulationErrorResult): Unit =
    messageDataAccumulationError(messageDataAccumulationErrorResult.request1.message.id, messageDataAccumulationErrorResult.exception)

  protected def messageDataAccumulationError(id: Long, exception: Exception): Unit = {
    removeFromMap(id)
    val e = new Exception("Problem during message data accumulation.", exception)
    amaConfig.broadcaster ! new Device.StopDevice(id, e)
  }

  protected def messageDataAccumulationSuccess(messageDataAccumulationSuccessResult: MessageDataAccumulator.MessageDataAccumulationSuccessResult): Unit =
    messageDataAccumulationSuccessResult.messageData.foreach { amaConfig.broadcaster ! new DeserializeWithId(messageDataAccumulationSuccessResult.request1.message.id, _) }

  protected def deserializationError(deserializationErrorResult: GeneralDeserializer.DeserializationErrorResult): Unit =
    deserializationError(deserializationErrorResult.request1.message.asInstanceOf[DeserializeWithId].id, deserializationErrorResult.exception)

  protected def deserializationError(id: Long, exception: Exception): Unit = {
    removeFromMap(id)
    val e = new Exception("Problem during message deserialization.", exception)
    amaConfig.broadcaster ! new Device.StopDevice(id, e)
  }

  protected def deserializationSuccess(deserializationSuccessResult: GeneralDeserializer.DeserializationSuccessResult): Unit =
    deserializationSuccess(deserializationSuccessResult.request1.message.asInstanceOf[DeserializeWithId].id, deserializationSuccessResult.deserializedMessageFromDevice)

  protected def deserializationSuccess(id: Long, messageFromDevice: MessageFromDevice): Unit = map.get(id) match {
    case Some(record) => amaConfig.broadcaster.tell(new Device.NewMessage(record.deviceInfo, messageFromDevice), record.deviceActor.getOrElse(self))

    case None => {
      val exception = new Exception(s"Received successfully deserialized message from device but id $id could not be found in internal map.")
      amaConfig.broadcaster ! new Device.StopDevice(id, exception)
    }
  }

  protected def putToMap(id: Long, record: Record): Unit = {
    map.put(id, record)
    log.debug(s"Remote address id $id was added (semi initialized device info ${record.deviceInfo}), currently there are ${map.size} ids in map (ids: ${map.keySet.mkString(",")}).")
  }

  protected def removeFromMap(id: Long): Unit = map.remove(id).map { record =>
    log.debug(s"Remote address id $id was removed (device info ${record.deviceInfo}), currently there are ${map.size} ids in map (ids: ${map.keySet.mkString(",")}).")
  }
}
