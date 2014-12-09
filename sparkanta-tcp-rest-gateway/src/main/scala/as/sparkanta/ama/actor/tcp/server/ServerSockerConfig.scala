package as.sparkanta.ama.actor.tcp.server

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object ServerSockerConfig {

  final val topConfigKey = classOf[ServerSockerConfig].getSimpleName

  final val localBindHostConfigKey = "localBindHost"
  final val localBindPortNumberConfigKey = "localBindPortNumber"

  /**
   * Assumes that Config contains:
   *
   *   ServerSockerConfig {
   *     localBindHostConfigKey = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): ServerSockerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   localBindHostConfigKey = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): ServerSockerConfig = {
    val localBindHost = config.getString(localBindHostConfigKey)
    val localBindPortNumber = config.getInt(localBindPortNumberConfigKey)

    new ServerSockerConfig(
      localBindHost,
      localBindPortNumber
    )
  }
}

class ServerSockerConfig(
  val localBindHost:       String,
  val localBindPortNumber: Int
)
