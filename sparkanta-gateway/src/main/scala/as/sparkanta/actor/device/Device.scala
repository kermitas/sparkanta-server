package as.sparkanta.actor.device

import akka.actor.{ ActorLogging, Actor, Props, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.actor.tcp.serversocket.ServerSocket
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.actor.message.MessageDataAccumulator
import as.sparkanta.actor.message.deserializer.Deserializer
import as.sparkanta.actor.inactivity.InactivityMonitor
import as.sparkanta.gateway.{ Device => DeviceSpec, NoAck }
import akka.io.Tcp
import as.sparkanta.device.message.todevice.Ping
import as.sparkanta.device.message.todevice.{ NoAck => DeviceNoAck }
import as.sparkanta.actor.message.serializer.Serializer
import as.sparkanta.gateway.DeviceAck

object Device {
  lazy final val maximumQueuedSendDataMessages = 50

  lazy final val warningTimeAfterMs = 2 * 1000
  lazy final val inactivityTimeAfterMs = 3 * 1000

  lazy final val waitingForDeviceIdentificationTimeoutInMs = 2 * 1000
  lazy final val speedTestTimeInMs: Option[Long] = Some(1 * 1000)

  lazy final val ping = new Ping
}

class Device(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import Device._

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new DeviceClassifier(amaConfig.broadcaster))
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {

    // ---------------

    case a: ServerSocket.NewConnection => {
      log.debug(s"New incoming connection ${a.connectionInfo.remote} hit ${a.connectionInfo.local}, asking socket to handle it.")
      amaConfig.broadcaster ! new StartDataAccumulationWithNewConnection(a)
    }

    // ---------------

    case a: MessageDataAccumulator.StartDataAccumulationResult => {

      val newConnection = a.request1.message.asInstanceOf[StartDataAccumulationWithNewConnection].newConnection

      a match {
        case a: MessageDataAccumulator.StartDataAccumulationSuccessResult => {

          val props = Props(new DeviceWorker(newConnection.connectionInfo, newConnection.akkaSocketTcpActor, amaConfig.broadcaster, waitingForDeviceIdentificationTimeoutInMs, speedTestTimeInMs, self))
          context.actorOf(props, name = classOf[DeviceWorker].getSimpleName + "-" + newConnection.connectionInfo.remote.id)

          amaConfig.broadcaster ! new Socket.ListenAt(newConnection.connectionInfo, newConnection.akkaSocketTcpActor, maximumQueuedSendDataMessages)
        }

        case a: MessageDataAccumulator.StartDataAccumulationErrorResult => {
          log.error(a.exception.get, s"Problem while starting message data accumulator for device of remote address id ${a.request1.message.id}, closing socket.")
          newConnection.akkaSocketTcpActor ! Tcp.Close
        }
      }
    }

    // ---------------

    case a: Socket.ListenAtResult => a match {
      case a: Socket.ListenAtSuccessResult => log.debug(s"Successfully handled new incoming connection ${a.request1.message.connectionInfo}.")

      case a: Socket.ListenAtErrorResult => {
        val e = new Exception(s"Could not handle new incoming connection ${a.request1.message.connectionInfo}.", a.exception)
        log.error(e, "")
        a.request1.message.akkaSocketTcpActor ! Tcp.Close

        amaConfig.broadcaster ! new DeviceSpec.DisconnectDevice(a.request1.message.connectionInfo.remote.id, e)

        amaConfig.broadcaster ! new MessageDataAccumulator.StopDataAccumulation(a.request1.message.connectionInfo.remote.id)
      }
    }

    // ---------------

    case a: MessageDataAccumulator.StopDataAccumulationResult =>

    // ---------------

    case a: Socket.NewData => {
      log.debug(s"Received ${a.data.size} bytes from device of remote address id ${a.request1.message.connectionInfo.remote.id}, asking accumulator to accumulate.")
      amaConfig.broadcaster ! new MessageDataAccumulator.AccumulateMessageData(a.request1.message.connectionInfo.remote.id, a.data)
    }

    // ---------------

    case a: MessageDataAccumulator.MessageDataAccumulationResult => a match {
      case a: MessageDataAccumulator.MessageDataAccumulationSuccessResult => a.messageData.get.foreach(messageData => amaConfig.broadcaster ! new DeserializeWithId(a.request1.message.id, messageData))

      case a: MessageDataAccumulator.MessageDataAccumulationErrorResult => {
        log.error(a.exception, s"Problem while accumulating data for device of remote address id ${a.request1.message.id}, closing socket.")
        amaConfig.broadcaster ! new Socket.StopListeningAt(a.request1.message.id)
      }
    }

    // ---------------

    case a: Deserializer.DeserializationResult => {
      val remoteAddressId = a.request1.message.asInstanceOf[DeserializeWithId].id

      a match {
        case a: Deserializer.DeserializationSuccessResult => {

          log.debug(s"New message ${a.deserializedMessageFromDevice.get} from device of remote address id $remoteAddressId come.")

          amaConfig.broadcaster ! new InactivityMonitor.Active(remoteAddressId)
        }

        case a: Deserializer.DeserializationErrorResult => {
          log.error(a.exception, s"Could not deserialize message for device of remote address id $remoteAddressId.")
          amaConfig.broadcaster ! new Socket.StopListeningAt(remoteAddressId)
        }
      }
    }

    // ---------------

    case a: Socket.StopListeningAtResult => a match {
      case a: Socket.StopListeningAtSuccessResult => if (a.wasListening.get) {
        val e = new Exception(s"Socket actor is not listening any more for connection of remote address id ${a.request1.message.id}, publishing ${classOf[DeviceSpec.DisconnectDevice].getClass.getSimpleName}.")
        amaConfig.broadcaster ! new DeviceSpec.DisconnectDevice(a.request1.message.id, e)
      }

      case a: Socket.StopListeningAtErrorResult => {
        log.error(a.exception, s"Problem while stopping socket listening for device of remote address id ${a.request1.message.id}, doing noting.")
      }
    }

    // ---------------

    case a: InactivityMonitor.InactivityWarning => {
      log.debug(s"Device of remote address id ${a.request1.message.id} was inactive for ${a.inactivityTimeInMs} milliseconds, sending Ping.")
      amaConfig.broadcaster ! new DeviceSpec.SendMessage(a.request1.message.id, ping, NoAck)
    }

    // ---------------

    case a: InactivityMonitor.InactivityDetected => {
      log.debug(s"Looks like device of remote address id ${a.request1.message.id} is inactive for more than ${a.inactivityTimeInMs} milliseconds, killing.")
      amaConfig.broadcaster ! new Socket.StopListeningAt(a.request1.message.id)
    }

    // ---------------

    case a: DeviceSpec.SendMessage => {
      amaConfig.broadcaster ! new SerializeWithSendMessage(a)
    }

    // ---------------

    case a: DeviceSpec.DeviceIsUp => {
      amaConfig.broadcaster ! new InactivityMonitor.StartInactivityMonitor(a.deviceInfo.connectionInfo.remote.id, warningTimeAfterMs, inactivityTimeAfterMs)
    }

    // ---------------

    case a: DeviceSpec.DeviceIsDown => {
      amaConfig.broadcaster ! new InactivityMonitor.StopInactivityMonitor(a.deviceInfo.connectionInfo.remote.id)
    }

    // ---------------

    case a: Socket.ListeningStopped => {
      amaConfig.broadcaster ! new MessageDataAccumulator.StopDataAccumulation(a.request1.message.connectionInfo.remote.id)
    }

    // ---------------

    case message => log.warning(s"Unhandled $message send by ${sender()}")

    // ---------------
  }
}

class StartDataAccumulationWithNewConnection(val newConnection: ServerSocket.NewConnection) extends MessageDataAccumulator.StartDataAccumulation(newConnection.connectionInfo.remote.id)

class DeserializeWithId(val id: Long, serializedMessageFromDevice: Array[Byte]) extends Deserializer.Deserialize(serializedMessageFromDevice)

class SerializeWithSendMessage(val sendMessage: DeviceSpec.SendMessage) extends Serializer.Serialize(sendMessage.messageToDevice, if (sendMessage.ack.isInstanceOf[DeviceAck]) sendMessage.ack.asInstanceOf[DeviceAck].deviceAck else DeviceNoAck)