package as.sparkanta.ama.actor.tcp.connection

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object SocketHandlerConfig {

  final val topConfigKey = classOf[SocketHandlerConfig].getSimpleName

  final val softwareVersionIdentificationTimeoutInSecondsConfigKey = "softwareVersionIdentificationTimeoutInSeconds"
  final val incomingDataInactivityTimeoutInSecondsConfigKey = "incomingDataInactivityTimeoutInSeconds"
  final val identificationStringConfigKey = "identificationString"

  /**
   * Assumes that Config contains:
   *
   *   SocketHandlerConfig {
   *     softwareVersionIdentificationTimeoutInSeconds = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): SocketHandlerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   softwareVersionIdentificationTimeoutInSeconds = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): SocketHandlerConfig = {
    val softwareVersionIdentificationTimeoutInSeconds = config.getInt(softwareVersionIdentificationTimeoutInSecondsConfigKey)
    val incomingDataInactivityTimeoutInSeconds = config.getInt(incomingDataInactivityTimeoutInSecondsConfigKey)
    val identificationString = config.getString(identificationStringConfigKey)

    new SocketHandlerConfig(softwareVersionIdentificationTimeoutInSeconds, incomingDataInactivityTimeoutInSeconds, identificationString)
  }
}

class SocketHandlerConfig(
  val softwareVersionIdentificationTimeoutInSeconds: Int,
  val incomingDataInactivityTimeoutInSeconds:        Int,
  val identificationString:                          String
)
