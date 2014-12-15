package as.sparkanta.device.message

object DeviceHello {
  lazy final val messageCode: Int = 1
}

class DeviceHello(val sparkDeviceId: String) extends MessageFormDevice with MessageToDevice {

  override def toString = s"${getClass.getSimpleName}(sparkDeviceId=$sparkDeviceId)"

}
