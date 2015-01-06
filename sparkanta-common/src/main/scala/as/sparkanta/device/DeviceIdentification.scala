package as.sparkanta.device

class DeviceIdentification(
  val softwareVersion: Int,
  val hardwareVersion: HardwareVersion,
  val deviceUniqueId:  Int
) extends Serializable {

  require(softwareVersion >= 0 && softwareVersion <= 255, s"Software version ($softwareVersion) can be only between 0 and 255.")
  require(deviceUniqueId >= 0 && deviceUniqueId <= 65535, s"Device unique id ($deviceUniqueId) can be only between 0 and 65535.")

  override def toString = s"${getClass.getSimpleName}(deviceUniqueId=$deviceUniqueId,softwareVersion=$softwareVersion,hardwareVersion=$hardwareVersion)"

}
