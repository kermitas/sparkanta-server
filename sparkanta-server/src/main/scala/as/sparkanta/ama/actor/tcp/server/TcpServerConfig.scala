package as.sparkanta.ama.actor.tcp.server

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object TcpServerConfig {

  final val topConfigKey = classOf[TcpServerConfig].getSimpleName

  final val localBindHostConfigKey = "localBindHost"
  final val localBindPortNumberConfigKey = "localBindPortNumber"

  /**
   * Assumes that Config contains:
   *
   *   TcpServerConfig {
   *     localBindHostConfigKey = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): TcpServerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   localBindHostConfigKey = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): TcpServerConfig = {
    val localBindHost = config.getString(localBindHostConfigKey)
    val localBindPortNumber = config.getInt(localBindPortNumberConfigKey)

    new TcpServerConfig(
      localBindHost,
      localBindPortNumber
    )
  }
}

class TcpServerConfig(
  val localBindHost:       String,
  val localBindPortNumber: Int
)
