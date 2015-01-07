package as.sparkanta.actor.device

import com.typesafe.config.{ Config, ConfigFactory }
/**
 * Configuration read from JSON (HOCON) file.
 */
object DeviceWorkerConfig {

  final val topConfigKey = classOf[DeviceWorkerConfig].getSimpleName

  final val warningTimeAfterMsConfigKey = "warningTimeAfterMs"
  final val inactivityTimeAfterMsConfigKey = "inactivityTimeAfterMs"
  final val extraTimeForWaitingOnSpeedTestResultInMsConfigKey = "extraTimeForWaitingOnSpeedTestResultInMs"
  final val waitingForSendDataResultTimeoutInMsIfNotSetInAckConfigKey = "waitingForSendDataResultTimeoutInMsIfNotSetInAck"

  /**
   * Assumes that Config contains:
   *
   *   DeviceWorkerConfig {
   *     warningTimeAfterMs = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): DeviceWorkerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   warningTimeAfterMs = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): DeviceWorkerConfig = {
    val warningTimeAfterMs = config.getLong(warningTimeAfterMsConfigKey)
    val inactivityTimeAfterMs = config.getLong(inactivityTimeAfterMsConfigKey)
    val extraTimeForWaitingOnSpeedTestResultInMs = config.getLong(extraTimeForWaitingOnSpeedTestResultInMsConfigKey)
    val waitingForSendDataResultTimeoutInMsIfNotSetInAck = config.getLong(waitingForSendDataResultTimeoutInMsIfNotSetInAckConfigKey)

    new DeviceWorkerConfig(warningTimeAfterMs, inactivityTimeAfterMs, extraTimeForWaitingOnSpeedTestResultInMs, waitingForSendDataResultTimeoutInMsIfNotSetInAck)
  }
}

class DeviceWorkerConfig(
  val warningTimeAfterMs:                               Long,
  val inactivityTimeAfterMs:                            Long,
  val extraTimeForWaitingOnSpeedTestResultInMs:         Long,
  val waitingForSendDataResultTimeoutInMsIfNotSetInAck: Long
)