package as.sparkanta.actor.message

import akka.actor.{ ActorLogging, Actor }
import akka.util.ByteString
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.gateway.NetworkDeviceInfo
import as.sparkanta.message.{ NewMessageDataFromDevice, NewDataFromDevice, NewIncomingConnection, ConnectionClosed, CouldNotDeserializeDataFromDevice }
import as.akka.broadcaster.Broadcaster
import scala.collection.mutable.Map

object MessageDataCollector {
  class Record(val networkDeviceInfo: NetworkDeviceInfo, var buffer: ByteString)
}

class MessageDataCollector(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import MessageDataCollector._

  protected val map = Map[Long, Record]()

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new MessageDataCollectorClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {
    case ndfd: NewDataFromDevice                => newDataFromDevice(ndfd)
    case nic: NewIncomingConnection             => newIncomingConnection(nic)
    case cc: ConnectionClosed                   => connectionClosed(cc)
    case cnd: CouldNotDeserializeDataFromDevice => // do nothing on that
    case message                                => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def newIncomingConnection(nic: NewIncomingConnection): Unit =
    map.put(nic.networkDeviceInfo.remoteAddress.id, new Record(nic.networkDeviceInfo, ByteString.empty))

  protected def connectionClosed(cc: ConnectionClosed): Unit = map.remove(cc.networkDeviceInfo.remoteAddress.id)

  protected def newDataFromDevice(ndfd: NewDataFromDevice): Unit = map.get(ndfd.networkDeviceInfo.remoteAddress.id).map { record =>
    record.buffer = record.buffer ++ ndfd.dataFromDevice
    analyzeBuffer(record)
  }

  protected def analyzeBuffer(record: Record): Unit = {
    val bufferSize = record.buffer.size

    if (bufferSize > 0 && bufferSize >= record.buffer(0)) {
      val messageDataAndRest = record.buffer.splitAt(record.buffer(0))
      record.buffer = messageDataAndRest._2

      amaConfig.broadcaster ! new NewMessageDataFromDevice(record.networkDeviceInfo, messageDataAndRest._1.toArray)
    }
  }
}