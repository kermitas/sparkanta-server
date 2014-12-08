package as.sparkanta.ama.actor.message.incoming

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object IncomingDataListenerConfig {

  final val topConfigKey = classOf[IncomingDataListenerConfig].getSimpleName

  final val sparkDeviceIdIdentificationTimeoutInSecondsConfigKey = "sparkDeviceIdIdentificationTimeoutInSeconds"
  final val sendPingOnIncomingDataInactivityIntervalInSecondsConfigKey = "sendPingOnIncomingDataInactivityIntervalInSeconds"

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

    new IncomingDataListenerConfig(sparkDeviceIdIdentificationTimeoutInSeconds, sendPingOnIncomingDataInactivityIntervalInSeconds)
  }
}

class IncomingDataListenerConfig(
  val sparkDeviceIdIdentificationTimeoutInSeconds:       Int,
  val sendPingOnIncomingDataInactivityIntervalInSeconds: Int
)
