package as.sparkanta.ama.actor.tcp.connection

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object TcpConnectionHandlerConfig {

  final val topConfigKey = classOf[TcpConnectionHandlerConfig].getSimpleName

  final val softwareVersionIdentificationTimeoutInSecondsConfigKey = "softwareVersionIdentificationTimeoutInSeconds"
  final val incomingDataInactivityTimeoutInSecondsConfigKey = "incomingDataInactivityTimeoutInSeconds"
  final val identificationStringConfigKey = "identificationString"

  /**
   * Assumes that Config contains:
   *
   *   TcpConnectionHandlerConfig {
   *     softwareVersionIdentificationTimeoutInSeconds = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): TcpConnectionHandlerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   softwareVersionIdentificationTimeoutInSeconds = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): TcpConnectionHandlerConfig = {
    val softwareVersionIdentificationTimeoutInSeconds = config.getInt(softwareVersionIdentificationTimeoutInSecondsConfigKey)
    val incomingDataInactivityTimeoutInSeconds = config.getInt(incomingDataInactivityTimeoutInSecondsConfigKey)
    val identificationString = config.getString(identificationStringConfigKey)

    new TcpConnectionHandlerConfig(softwareVersionIdentificationTimeoutInSeconds, incomingDataInactivityTimeoutInSeconds, identificationString)
  }
}

class TcpConnectionHandlerConfig(
  val softwareVersionIdentificationTimeoutInSeconds: Int,
  val incomingDataInactivityTimeoutInSeconds:        Int,
  val identificationString:                          String
)
