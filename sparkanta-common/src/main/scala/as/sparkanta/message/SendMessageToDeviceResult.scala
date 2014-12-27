package as.sparkanta.message

sealed class SendMessageToDeviceResult(val sendMessageToDevice: SendMessageToDevice, val exception: Option[Exception]) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(sendMessageToDevice=$sendMessageToDevice,exception=$exception)"

}

class SendMessageToDeviceSuccessResult(sendMessageToDevice: SendMessageToDevice) extends SendMessageToDeviceResult(sendMessageToDevice, None)

class SendMessageToDeviceErrorResult(sendMessageToDevice: SendMessageToDevice, exception: Exception) extends SendMessageToDeviceResult(sendMessageToDevice, Some(exception))