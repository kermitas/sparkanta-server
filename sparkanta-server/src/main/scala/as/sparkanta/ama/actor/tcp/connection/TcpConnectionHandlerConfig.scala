package as.sparkanta.ama.actor.tcp.connection

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object TcpConnectionHandlerConfig {

  final val topConfigKey = classOf[TcpConnectionHandlerConfig].getSimpleName

  final val inactivityTimeoutInSecondsConfigKey = "inactivityTimeoutInSeconds"

  /**
   * Assumes that Config contains:
   *
   *   TcpConnectionHandlerConfig {
   *     inactivityTimeoutInSeconds = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): TcpConnectionHandlerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   inactivityTimeoutInSeconds = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): TcpConnectionHandlerConfig = {
    val inactivityTimeoutInSeconds = config.getLong(inactivityTimeoutInSecondsConfigKey)

    new TcpConnectionHandlerConfig(inactivityTimeoutInSeconds)
  }
}

class TcpConnectionHandlerConfig(
  val inactivityTimeoutInSeconds: Long
)
