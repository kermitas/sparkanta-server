package as.sparkanta.ama.actor.tcp.connection

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object TcpConnectionHandlerConfig {

  final val topConfigKey = classOf[TcpConnectionHandlerConfig].getSimpleName

  final val identificationTimeoutInSecondsConfigKey = "identificationTimeoutInSeconds"
  final val inactivityTimeoutInSecondsConfigKey = "inactivityTimeoutInSeconds"
  final val identificationStringConfigKey = "identificationString"

  /**
   * Assumes that Config contains:
   *
   *   TcpConnectionHandlerConfig {
   *     identificationTimeoutInSeconds = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): TcpConnectionHandlerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   identificationTimeoutInSeconds = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): TcpConnectionHandlerConfig = {
    val identificationTimeoutInSeconds = config.getInt(identificationTimeoutInSecondsConfigKey)
    val inactivityTimeoutInSeconds = config.getInt(inactivityTimeoutInSecondsConfigKey)
    val identificationString = config.getString(identificationStringConfigKey)

    new TcpConnectionHandlerConfig(identificationTimeoutInSeconds, inactivityTimeoutInSeconds, identificationString)
  }
}

class TcpConnectionHandlerConfig(
  val identificationTimeoutInSeconds: Int,
  val inactivityTimeoutInSeconds:     Int,
  val identificationString:           String
)
