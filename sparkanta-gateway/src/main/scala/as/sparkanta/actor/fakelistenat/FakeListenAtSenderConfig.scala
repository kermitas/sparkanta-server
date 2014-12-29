/*
package as.sparkanta.actor.fakelistenat

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object FakeListenAtSenderConfig {

  final val topConfigKey = classOf[FakeListenAtSenderConfig].getSimpleName

  final val listenIdConfigKey = "listenId"
  final val listenIpConfigKey = "listenIp"
  final val listenPortConfigKey = "listenPort"
  final val openingServerSocketTimeoutInSecondsConfigKey = "openingServerSocketTimeoutInSeconds"
  final val keepServerSocketOpenTimeoutInSecondsConfigKey = "keepServerSocketOpenTimeoutInSeconds"
  final val forwardToRestIdConfigKey = "forwardToRestId"
  final val forwardToRestIpConfigKey = "forwardToRestIp"
  final val forwardToRestPortConfigKey = "forwardToRestPort"
  final val resendListenAtIntervalInSecondsConfigKey = "resendListenAtIntervalInSeconds"

  /**
   * Assumes that Config contains:
   *
   *   FakeListenAtSenderConfig {
   *     listenIp = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): FakeListenAtSenderConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   listenIp = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): FakeListenAtSenderConfig = {
    val listenId = config.getLong(listenIdConfigKey)
    val listenIp = config.getString(listenIpConfigKey)
    val listenPort = config.getInt(listenPortConfigKey)
    val openingServerSocketTimeoutInSeconds = config.getInt(openingServerSocketTimeoutInSecondsConfigKey)
    val keepServerSocketOpenTimeoutInSeconds = config.getInt(keepServerSocketOpenTimeoutInSecondsConfigKey)
    val forwardToRestId = config.getLong(forwardToRestIdConfigKey)
    val forwardToRestIp = config.getString(forwardToRestIpConfigKey)
    val forwardToRestPort = config.getInt(forwardToRestPortConfigKey)
    val resendListenAtIntervalInSeconds = config.getInt(resendListenAtIntervalInSecondsConfigKey)

    new FakeListenAtSenderConfig(
      listenId,
      listenIp,
      listenPort,
      openingServerSocketTimeoutInSeconds,
      keepServerSocketOpenTimeoutInSeconds,
      forwardToRestId,
      forwardToRestIp,
      forwardToRestPort,
      resendListenAtIntervalInSeconds
    )
  }
}

class FakeListenAtSenderConfig(
  val listenId:                             Long,
  val listenIp:                             String,
  val listenPort:                           Int,
  val openingServerSocketTimeoutInSeconds:  Int,
  val keepServerSocketOpenTimeoutInSeconds: Int,
  val forwardToRestId:                      Long,
  val forwardToRestIp:                      String,
  val forwardToRestPort:                    Int,
  val resendListenAtIntervalInSeconds:      Int
)
*/ 