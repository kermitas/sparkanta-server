package as.sparkanta.ama.actor.tcp.message

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object IncomingDataListenerConfig {

  final val topConfigKey = classOf[IncomingDataListenerConfig].getSimpleName

  final val sparkDeviceIdIdentificationTimeoutInSecondsConfigKey = "sparkDeviceIdIdentificationTimeoutInSeconds"

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

    new IncomingDataListenerConfig(sparkDeviceIdIdentificationTimeoutInSeconds)
  }
}

class IncomingDataListenerConfig(
  val sparkDeviceIdIdentificationTimeoutInSeconds: Int
)
