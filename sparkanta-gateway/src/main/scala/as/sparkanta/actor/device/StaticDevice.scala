package as.sparkanta.actor.device

import akka.actor.{ ActorLogging, Actor }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.actor.tcp.serversocket.ServerSocket
import as.sparkanta.actor.tcp.socket.Socket
import as.sparkanta.actor.message.MessageDataAccumulator
import as.sparkanta.actor.message.deserializer.Deserializer
import as.sparkanta.actor.inactivity.InactivityMonitor
import as.sparkanta.gateway.Device
import akka.io.Tcp
import as.sparkanta.device.message.todevice.Ping
import as.sparkanta.gateway.NoAck

object StaticDevice {
  lazy final val maximumQueuedSendDataMessages = 50

  lazy final val warningTimeAfterMs = 2 * 1000
  lazy final val inactivityTimeAfterMs = 3 * 1000

  lazy final val ping = new Ping
}

class StaticDevice(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import StaticDevice._

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new StaticDeviceClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {

    case a: ServerSocket.NewConnection => {
      log.debug(s"New incoming connection ${a.connectionInfo.remote} hit ${a.connectionInfo.local}, asking socket to handle it.")

      // TODO start accumulator for this id
      // TODO start dedicated Device actor

      amaConfig.broadcaster ! new Socket.ListenAt(a.connectionInfo, a.akkaSocketTcpActor, maximumQueuedSendDataMessages)
    }

    case a: Socket.ListenAtResult => a match {
      case a: Socket.ListenAtSuccessResult => log.debug(s"Successfully handled new incoming connection ${a.request1.message.connectionInfo}.")

      case a: Socket.ListenAtErrorResult => {
        log.error(a.exception, s"Could not handle new incoming connection ${a.request1.message.connectionInfo}.")
        a.request1.message.akkaSocketTcpActor ! Tcp.Close

        // TODO somehow stop dedicated Device actor

        // TODO stop accumulator for this id
        amaConfig.broadcaster ! new MessageDataAccumulator.ClearData(a.request1.message.connectionInfo.remote.id)
      }
    }

    case a: Socket.NewData => {
      log.debug(s"Received ${a.data.size} bytes from device of remote address id ${a.request1.message.connectionInfo.remote.id}, asking accumulator to accumulate.")
      amaConfig.broadcaster ! new MessageDataAccumulator.AccumulateMessageData(a.request1.message.connectionInfo.remote.id, a.data)
    }

    case a: MessageDataAccumulator.MessageDataAccumulationResult => a match {
      case a: MessageDataAccumulator.MessageDataAccumulationSuccessResult => a.messageData.get.foreach(messageData => amaConfig.broadcaster ! new DeserializeWithId(a.request1.message.id, messageData))

      case a: MessageDataAccumulator.MessageDataAccumulationErrorResult => {
        log.error(a.exception, s"Problem while accumulating data for device of remote address id ${a.request1.message.id}.")
        amaConfig.broadcaster ! new Socket.StopListeningAt(a.request1.message.id)
      }
    }

    case a: Deserializer.DeserializationResult => {
      val remoteAddressId = a.request1.message.asInstanceOf[DeserializeWithId].id

      a match {
        case a: Deserializer.DeserializationSuccessResult => {

          log.debug(s"New message ${a.deserializedMessageFromDevice.get} from device of remote address id $remoteAddressId come.")

          // TODO dedicated Device actor should publish that new message come

          amaConfig.broadcaster ! new InactivityMonitor.Active(remoteAddressId)
        }

        case a: Deserializer.DeserializationErrorResult => {
          log.error(a.exception, s"Could not deserialize message for device of remote address id $remoteAddressId.")
          amaConfig.broadcaster ! new Socket.StopListeningAt(remoteAddressId)
        }
      }
    }

    case a: Socket.StopListeningAtResult => a match {
      case a: Socket.StopListeningAtSuccessResult => if (a.wasListening.get) {
        val e = new Exception(s"Socket actor is not listening any more for connection of remote address id ${a.request1.message.id}.")
        amaConfig.broadcaster ! new Device.DisconnectDevice(a.request1.message.id, e)
      }

      case a: Socket.StopListeningAtErrorResult => {
        log.error(a.exception, s"Problem while stopping socket listening for device of remote address id ${a.request1.message.id}.")
      }
    }

    case a: InactivityMonitor.InactivityWarning => {
      log.debug(s"Device of remote address id ${a.request1.message.id} was inactive for ${a.inactivityTimeInMs} milliseconds, sending Ping.")
      amaConfig.broadcaster ! new Device.SendMessage(a.request1.message.id, ping, NoAck)
    }

    case a: InactivityMonitor.InactivityDetected => {
      log.debug(s"Looks like device of remote address id ${a.request1.message.id} is inactive for more than ${a.inactivityTimeInMs} milliseconds, killing.")
      amaConfig.broadcaster ! new Socket.StopListeningAt(a.request1.message.id)
    }

    case a: Device.DeviceIsUp => {
      amaConfig.broadcaster ! new InactivityMonitor.StartInactivityMonitor(a.deviceInfo.connectionInfo.remote.id, warningTimeAfterMs, inactivityTimeAfterMs)
    }

    case a: Device.DeviceIsDown => {
      amaConfig.broadcaster ! new InactivityMonitor.StopInactivityMonitor(a.deviceInfo.connectionInfo.remote.id)
    }

    /*
    case a: Socket.ListeningStarted => {
    }
    */

    case a: Socket.ListeningStopped => {

      // TODO stop accumulator for this id
      amaConfig.broadcaster ! new MessageDataAccumulator.ClearData(a.request1.message.connectionInfo.remote.id)
    }

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }
}

class DeserializeWithId(val id: Long, serializedMessageFromDevice: Array[Byte]) extends Deserializer.Deserialize(serializedMessageFromDevice)