package as.sparkanta.message

class DisconnectDevice(val remoteAddressId: Long, val delayBeforeNextConnectionAttemptInSeconds: Int) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(remoteAddressId=$remoteAddressId,delayBeforeNextConnectionAttemptInSeconds=$delayBeforeNextConnectionAttemptInSeconds)"

}