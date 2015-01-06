package as.sparkanta.actor.device.inactivity

import com.typesafe.config.{ Config, ConfigFactory }
/**
 * Configuration read from JSON (HOCON) file.
 */
object InactivityMonitorConfig {

  final val topConfigKey = classOf[InactivityMonitorConfig].getSimpleName

  final val warningTimeAfterMsConfigKey = "warningTimeAfterMs"
  final val inactivityTimeAfterMsConfigKey = "inactivityTimeAfterMs"

  /**
   * Assumes that Config contains:
   *
   *   InactivityMonitorConfig {
   *     warningTimeAfterMs = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): InactivityMonitorConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   warningTimeAfterMs = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): InactivityMonitorConfig = {
    val warningTimeAfterMs = config.getInt(warningTimeAfterMsConfigKey)
    val inactivityTimeAfterMs = config.getInt(inactivityTimeAfterMsConfigKey)

    new InactivityMonitorConfig(warningTimeAfterMs, inactivityTimeAfterMs)
  }
}

class InactivityMonitorConfig(
  val warningTimeAfterMs:    Int,
  val inactivityTimeAfterMs: Int
)