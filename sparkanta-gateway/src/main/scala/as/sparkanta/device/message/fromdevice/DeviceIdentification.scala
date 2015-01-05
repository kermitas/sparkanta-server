package as.sparkanta.device.message.fromdevice

import as.sparkanta.device.{ HardwareVersion, DeviceIdentification => DeviceIdentificationSpec }

object DeviceIdentification {
  lazy final val messageCode: Int = 1
}

class DeviceIdentification(
  sparkantaIdentificationString: String,
  softwareVersion:               Int,
  hardwareVersion:               HardwareVersion,
  deviceUniqueId:                Int,
  deviceUniqueName:              String
) extends DeviceIdentificationSpec(sparkantaIdentificationString, softwareVersion, hardwareVersion, deviceUniqueId, deviceUniqueName) with MessageFromDeviceThatShouldNotBeForwardedToRestServer