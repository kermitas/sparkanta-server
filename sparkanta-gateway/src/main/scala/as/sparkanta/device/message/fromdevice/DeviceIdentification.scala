package as.sparkanta.device.message.fromdevice

import as.sparkanta.device.{ HardwareVersion, DeviceIdentification => DeviceIdentificationSpec }

object DeviceIdentification {
  lazy final val messageCode: Int = 1
}

class DeviceIdentification(
  softwareVersion: Int,
  hardwareVersion: HardwareVersion,
  deviceUniqueId:  Int
) extends DeviceIdentificationSpec(softwareVersion, hardwareVersion, deviceUniqueId) with MessageFromDeviceThatShouldNotBeForwardedToRestServer