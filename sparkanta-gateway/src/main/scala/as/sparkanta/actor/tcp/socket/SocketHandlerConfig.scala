/*
package as.sparkanta.actor.tcp.socket

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object SocketHandlerConfig {

  final val topConfigKey = classOf[SocketHandlerConfig].getSimpleName

  final val identificationTimeoutInSecondsConfigKey = "identificationTimeoutInSeconds"
  final val incomingDataInactivityTimeoutInSecondsConfigKey = "incomingDataInactivityTimeoutInSeconds"
  final val identificationStringConfigKey = "identificationString"
  final val stressTestTimeInSecondsConfigKey = "stressTestTimeInSeconds"

  /**
   * Assumes that Config contains:
   *
   *   SocketHandlerConfig {
   *     identificationTimeoutInSeconds = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): SocketHandlerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   identificationTimeoutInSeconds = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): SocketHandlerConfig = {
    val identificationTimeoutInSeconds = config.getInt(identificationTimeoutInSecondsConfigKey)
    val incomingDataInactivityTimeoutInSeconds = config.getInt(incomingDataInactivityTimeoutInSecondsConfigKey)
    val identificationString = config.getString(identificationStringConfigKey)
    val stressTestTimeInSeconds = optionallyReadLongGreaterThanZero(stressTestTimeInSecondsConfigKey, config)

    new SocketHandlerConfig(identificationTimeoutInSeconds, incomingDataInactivityTimeoutInSeconds, identificationString, stressTestTimeInSeconds)
  }

  protected def optionallyReadLongGreaterThanZero(path: String, config: Config): Option[Long] = if (config.hasPath(path)) {
    val value = config.getLong(path)

    if (value > 0)
      Some(value)
    else
      None

  } else {
    None
  }
}

class SocketHandlerConfig(
  val identificationTimeoutInSeconds:         Int,
  val incomingDataInactivityTimeoutInSeconds: Int,
  val identificationString:                   String,
  val stressTestTimeInSeconds:                Option[Long]
)
*/ 