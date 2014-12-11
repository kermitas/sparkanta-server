package as.sparkanta.gateway.message

class DataToDeviceSendConfirmation(val successfullySendDataToDevice: DataToDevice) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(successfullySendDataToDevice=$successfullySendDataToDevice)"

}