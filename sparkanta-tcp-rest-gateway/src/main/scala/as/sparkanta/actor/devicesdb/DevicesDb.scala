/*
package as.sparkanta.actor.devicesdb

import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.akka.broadcaster.Broadcaster
import as.sparkanta.gateway.message.{ SparkDeviceIdWasIdentified, SoftwareAndHardwareVersionWasIdentified, NewIncomingConnection, ConnectionClosed }
import scala.collection.immutable.Seq
import as.sparkanta.gateway.message.{ GetCurrentDevices, CurrentDevices }
import as.sparkanta.gateway.{ NetworkDeviceInfo, SoftwareAndHardwareIdentifiedDeviceInfo, SparkDeviceIdIdentifiedDeviceInfo }

class DevicesDb(amaConfig: AmaConfig) extends Actor with ActorLogging {

  protected var devices = Seq[NetworkDeviceInfo]()

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

      devices = devices :+ new NetworkDeviceInfo(nic.remoteAddress, nic.localAddress)
      log.debug(s"Device of remoteAddressId ${nic.remoteAddress.id} added to db, currently there are ${devices.size} devices in db.")
    }

    case svwi: SoftwareAndHardwareVersionWasIdentified => devices.find(r => r.remoteAddress.id == svwi.deviceInfo.remoteAddress.id).map { d =>
      devices = devices.filterNot(_ == d)
      devices :+ svwi.deviceInfo
    }

    case sdiwi: SparkDeviceIdWasIdentified => devices.find(r => r.remoteAddress.id == sdiwi.deviceInfo.remoteAddress.id).map(_.asInstanceOf[SoftwareAndHardwareIdentifiedDeviceInfo]).map { d =>
      devices = devices.filterNot(_ == d)
      devices :+ sdiwi.deviceInfo
    }

    case cc: ConnectionClosed => {
      devices = devices.filterNot(_.remoteAddress.id == cc.deviceInfo.remoteAddress.id)
      log.debug(s"Device of remoteAddressId ${cc.deviceInfo.remoteAddress.id} was removed from db, currently there are ${devices.size} devices in db.")
    }

    case gad: GetCurrentDevices => sender() ! new CurrentDevices(devices)

    case message                => log.warning(s"Unhandled $message send by ${sender()}")
  }
}
*/ 