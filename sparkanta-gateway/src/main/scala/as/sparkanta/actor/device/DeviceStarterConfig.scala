package as.sparkanta.actor.device

import com.typesafe.config.{ Config, ConfigFactory }
/**
 * Configuration read from JSON (HOCON) file.
 */
object DeviceStarterConfig {

  final val topConfigKey = classOf[DeviceStarterConfig].getSimpleName

  final val maximumQueuedSendDataMessagesConfigKey = "maximumQueuedSendDataMessages"
  final val deviceIdentificationTimeoutInMsConfigKey = "deviceIdentificationTimeoutInMs"
  final val pingPongSpeedTestTimeInMsConfigKey = "pingPongSpeedTestTimeInMs"

  /**
   * Assumes that Config contains:
   *
   *   DeviceStarterConfig {
   *     maximumQueuedSendDataMessages = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): DeviceStarterConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   maximumQueuedSendDataMessages = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): DeviceStarterConfig = {
    val maximumQueuedSendDataMessages = config.getInt(maximumQueuedSendDataMessagesConfigKey)
    val deviceIdentificationTimeoutInMs = config.getInt(deviceIdentificationTimeoutInMsConfigKey)
    val pingPongSpeedTestTimeInMs = if (config.hasPath(pingPongSpeedTestTimeInMsConfigKey)) Some(config.getLong(pingPongSpeedTestTimeInMsConfigKey)) else None

    new DeviceStarterConfig(maximumQueuedSendDataMessages, deviceIdentificationTimeoutInMs, pingPongSpeedTestTimeInMs)
  }
}

class DeviceStarterConfig(
  val maximumQueuedSendDataMessages:   Int,
  val deviceIdentificationTimeoutInMs: Int,
  val pingPongSpeedTestTimeInMs:       Option[Long]
)