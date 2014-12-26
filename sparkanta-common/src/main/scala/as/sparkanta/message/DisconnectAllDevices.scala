package as.sparkanta.server.message

class DisconnectAllDevices(val delayBeforeNextConnectionAttemptInSeconds: Int) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(delayBeforeNextConnectionAttemptInSeconds=$delayBeforeNextConnectionAttemptInSeconds)"

}