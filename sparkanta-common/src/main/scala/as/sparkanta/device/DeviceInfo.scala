package as.sparkanta.device

import scala.net.IdentifiedConnectionInfo

class DeviceInfo(
  val connectionInfo:           IdentifiedConnectionInfo,
  val deviceIdentification:     DeviceIdentification,
  val pingPongsCountInTimeInMs: Option[(Long, Long)]
) extends Serializable {

  def timeInSystemInMillis: Long = System.currentTimeMillis - connectionInfo.startTimeInMs

  override def toString = s"${getClass.getSimpleName}(deviceIdentification=$deviceIdentification,connectionInfo=$connectionInfo,timeInSystem=$timeInSystemInMillis,pingPongsCountInTimeInMs=$pingPongsCountInTimeInMs)"
}