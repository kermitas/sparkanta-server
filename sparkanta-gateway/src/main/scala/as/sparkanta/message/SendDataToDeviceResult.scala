package as.sparkanta.message

sealed abstract class SendDataToDeviceResult(val sendDataToDevice: SendDataToDevice, val exception: Option[Exception]) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(sendDataToDevice=$sendDataToDevice,exception=$exception)"

}

class SendDataToDeviceSuccessResult(sendDataToDevice: SendDataToDevice) extends SendDataToDeviceResult(sendDataToDevice, None)

class SendDataToDeviceErrorResult(sendDataToDevice: SendDataToDevice, exception: Exception) extends SendDataToDeviceResult(sendDataToDevice, Some(exception))