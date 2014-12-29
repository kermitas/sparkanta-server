package as.sparkanta.device.message.fromdevice

object DeviceIdentification {
  lazy final val messageCode: Int = 1
}

class DeviceIdentification(
  val sparkantaIdentificationString: String,
  val softwareVersion:               Int,
  val hardwareVersion:               Int,
  val deviceUniqueId:                Int,
  val deviceUniqueName:              String
) extends MessageFormDevice with DoNotForwardToRestServer {

  require(sparkantaIdentificationString.length <= 255, s"Sparkanta identification string ('$sparkantaIdentificationString') should not be longer than 255 characters.")
  require(softwareVersion >= 0 && softwareVersion <= 255, s"Software version ($softwareVersion) can be only between 0 and 255.")
  require(hardwareVersion >= 0 && hardwareVersion <= 255, s"Hardware version ($hardwareVersion) can be only between 0 and 255.")
  require(deviceUniqueId >= 0 && deviceUniqueId <= 65535, s"Device unique id ($deviceUniqueId) can be only between 0 and 65535.")
  require(deviceUniqueName.length <= 255, s"Device unique name ('$deviceUniqueName') should not be longer than 255 characters.")

  override def toString = s"${getClass.getSimpleName}(deviceUniqueId=$deviceUniqueId,deviceUniqueName='$deviceUniqueName',softwareVersion=$softwareVersion,hardwareVersion=$hardwareVersion,sparkantaIdentificationString='$sparkantaIdentificationString')"

}
