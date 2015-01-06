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
    val warningTimeAfterMs = config.getInt(warningTimeAfterMsConfigKey)
    val inactivityTimeAfterMs = config.getInt(inactivityTimeAfterMsConfigKey)
    val extraTimeForWaitingOnSpeedTestResultInMs = config.getInt(extraTimeForWaitingOnSpeedTestResultInMsConfigKey)

    new DeviceWorkerConfig(warningTimeAfterMs, inactivityTimeAfterMs, extraTimeForWaitingOnSpeedTestResultInMs)
  }
}

class DeviceWorkerConfig(
  val warningTimeAfterMs:                       Int,
  val inactivityTimeAfterMs:                    Int,
  val extraTimeForWaitingOnSpeedTestResultInMs: Int
)