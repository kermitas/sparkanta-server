package as.sparkanta.actor2.device

import akka.actor.{ ActorLogging, Actor }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.actor2.tcp.serversocket.ServerSocket
import as.sparkanta.actor2.tcp.socket.Socket
import as.sparkanta.actor2.message.MessageDataAccumulator
import as.sparkanta.actor2.message.deserializer.Deserializer
import as.sparkanta.actor2.inactivity.InactivityMonitor

object StaticDevice {
  lazy final val maximumQueuedSendDataMessages = 50
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
    case a: ServerSocket.NewConnection => amaConfig.broadcaster ! new Socket.ListenAt(a.connectionInfo, a.akkaSocketTcpActor, maximumQueuedSendDataMessages) // TODO .. and receive result!

    // on Socket.ListenAtSuccessResult:
    // - start Device actor

    // on DeviceUp:
    // - start inactivity monitoring

    // on every deserialized message send that this device is active

    // on inactivity warning send Ping to device

    // on inactivity timeout publish ?WHAT? on broadcaster

    case a: Socket.NewData             => amaConfig.broadcaster ! new MessageDataAccumulator.AccumulateMessageData(a.data, a.request1.message.connectionInfo.remote.id)

    case a: Socket.ListeningStopped => { // TODO: do this BUT on DeviceDown !!
      amaConfig.broadcaster ! new MessageDataAccumulator.ClearData(a.request1.message.connectionInfo.remote.id)
      amaConfig.broadcaster ! new InactivityMonitor.StopInactivityMonitor(a.request1.message.connectionInfo.remote.id)
    }

    case a: MessageDataAccumulator.MessageDataAccumulationResult => a match {
      case a: MessageDataAccumulator.MessageDataAccumulationSuccessResult => a.messageData.get.foreach(messageData => amaConfig.broadcaster ! new Deserializer.Deserialize(messageData))
      case a: MessageDataAccumulator.MessageDataAccumulationErrorResult   => // TODO publish ?WHAT? on broadcaster
    }

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }
}
