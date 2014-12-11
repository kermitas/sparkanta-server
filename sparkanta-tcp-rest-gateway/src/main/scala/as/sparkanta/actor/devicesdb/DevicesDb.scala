package as.sparkanta.actor.devicesdb

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
    case nic: NewIncomingConnection => if (devices.find(r => r.remoteAddress.id == nic.remoteAddress.id).isEmpty) {

      devices = devices :+ new DeviceRecord(nic.remoteAddress, nic.localAddress)
      log.debug(s"Device of remoteAddressId ${nic.remoteAddress.id} added to db, currently there are ${devices.size} devices in db.")
    }

    case svwi: SoftwareVersionWasIdentified => devices.find(r => r.remoteAddress.id == svwi.remoteAddress.id).map { deviceRecord =>
      devices = devices.filterNot(_ == deviceRecord)
      devices :+ deviceRecord.copy(softwareVersion = Some(svwi.softwareVersion))
    }

    case sdiwi: SparkDeviceIdWasIdentified => devices.find(r => r.remoteAddress.id == sdiwi.remoteAddress.id).map { deviceRecord =>
      devices = devices.filterNot(_ == deviceRecord)
      devices :+ deviceRecord.copy(sparkDeviceId = Some(sdiwi.sparkDeviceId))
    }

    case cc: ConnectionClosed => {
      devices = devices.filterNot(_.remoteAddress.id == cc.remoteAddress.id)
      log.debug(s"Device of remoteAddressId ${cc.remoteAddress.id} was removed from db, currently there are ${devices.size} devices in db.")
    }

    case gad: GetCurrentDevices => sender() ! new CurrentDevices(devices)

    case message                => log.warning(s"Unhandled $message send by ${sender()}")
  }
}