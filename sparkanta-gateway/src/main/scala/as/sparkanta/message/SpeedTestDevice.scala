package as.sparkanta.message

class SpeedTestDevice(val remoteAddressId: Long, val testTimeInSeconds: Int) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(remoteAddressId=$remoteAddressId,testTimeInSeconds=$testTimeInSeconds)"

}
