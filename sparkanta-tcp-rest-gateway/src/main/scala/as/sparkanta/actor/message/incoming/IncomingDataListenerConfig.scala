/*
package as.sparkanta.actor.message.incoming

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object IncomingDataListenerConfig {

  final val topConfigKey = classOf[IncomingDataListenerConfig].getSimpleName

  final val sparkDeviceIdIdentificationTimeoutInSecondsConfigKey = "sparkDeviceIdIdentificationTimeoutInSeconds"
  final val sendPingOnIncomingDataInactivityIntervalInSecondsConfigKey = "sendPingOnIncomingDataInactivityIntervalInSeconds"
  final val stressTestTimeoutInSecondsConfigKey = "stressTestTimeoutInSeconds"

  /**
   * Assumes that Config contains:
   *
   *   IncomingDataListenerConfig {
   *     sparkDeviceIdIdentificationTimeoutInSeconds = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): IncomingDataListenerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   sparkDeviceIdIdentificationTimeoutInSeconds = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): IncomingDataListenerConfig = {
    val sparkDeviceIdIdentificationTimeoutInSeconds = config.getInt(sparkDeviceIdIdentificationTimeoutInSecondsConfigKey)
    val sendPingOnIncomingDataInactivityIntervalInSeconds = config.getInt(sendPingOnIncomingDataInactivityIntervalInSecondsConfigKey)
    val stressTestTimeoutInSeconds = optionallyReadLongGreaterThanZero(stressTestTimeoutInSecondsConfigKey, config)

    new IncomingDataListenerConfig(sparkDeviceIdIdentificationTimeoutInSeconds, sendPingOnIncomingDataInactivityIntervalInSeconds, stressTestTimeoutInSeconds)
  }

  protected def optionallyReadLongGreaterThanZero(path: String, config: Config): Option[Long] = if (config.hasPath(stressTestTimeoutInSecondsConfigKey)) {
    val stressTestTimeoutInSeconds = config.getLong(stressTestTimeoutInSecondsConfigKey)
    if (stressTestTimeoutInSeconds > 0)
      Some(stressTestTimeoutInSeconds)
    else
      None
  } else {
    None
  }
}

class IncomingDataListenerConfig(
  val sparkDeviceIdIdentificationTimeoutInSeconds:       Int,
  val sendPingOnIncomingDataInactivityIntervalInSeconds: Int,
  val stressTestTimeoutInSeconds:                        Option[Long]
)

*/ 