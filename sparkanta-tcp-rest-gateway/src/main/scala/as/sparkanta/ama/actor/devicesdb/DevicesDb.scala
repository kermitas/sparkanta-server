package as.sparkanta.ama.actor.devicesdb

import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.akka.broadcaster.Broadcaster
import as.sparkanta.gateway.message.{ SparkDeviceIdWasIdentified, SoftwareVersionWasIdentified, NewIncomingConnection, ConnectionClosed }
import scala.collection.immutable.Seq
import as.sparkanta.gateway.message.{ GetCurrentDevices, CurrentDevices, DeviceRecord }

class DevicesDb(amaConfig: AmaConfig) extends Actor with ActorLogging {

  protected var devices = Seq[DeviceRecord]()

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    try {
      // notifying broadcaster to register us with given classifier
      amaConfig.broadcaster ! new Broadcaster.Register(self, new DevicesDbClassifier)

      amaConfig.sendInitializationResult()
    } catch {
      case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
    }
  }

  override def receive = {
    case nic: NewIncomingConnection => if (devices.find(r => r.runtimeId == nic.runtimeId).isEmpty) {

      devices = devices :+ new DeviceRecord(nic.runtimeId, nic.remoteIp, nic.remotePort, nic.localIp, nic.localPort)
      log.debug(s"Device of runtimeId ${nic.runtimeId} added to db, currently there are ${devices.size} devices in db.")
    }

    case svwi: SoftwareVersionWasIdentified => devices.find(r => r.runtimeId == svwi.runtimeId).map { deviceRecord =>
      devices = devices.filterNot(_ == deviceRecord)
      devices :+ deviceRecord.copy(softwareVersion = Some(svwi.softwareVersion))
    }

    case sdiwi: SparkDeviceIdWasIdentified => devices.find(r => r.runtimeId == sdiwi.runtimeId).map { deviceRecord =>
      devices = devices.filterNot(_ == deviceRecord)
      devices :+ deviceRecord.copy(sparkDeviceId = Some(sdiwi.sparkDeviceId))
    }

    case cc: ConnectionClosed => {
      devices = devices.filterNot(_.runtimeId == cc.runtimeId)
      log.debug(s"Device of runtimeId ${cc.runtimeId} was removed from db, currently there are ${devices.size} devices in db.")
    }

    case gad: GetCurrentDevices => sender() ! new CurrentDevices(devices)

    case message                => log.warning(s"Unhandled $message send by ${sender()}")
  }
}