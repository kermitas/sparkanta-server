package as.sparkanta.ama.actor.tcp.message

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object OutgoingMessageListenerConfig {

  final val topConfigKey = classOf[OutgoingMessageListenerConfig].getSimpleName

  final val waitingForAckAfterSendingDisconnectTimeoutInSecondsConfigKey = "waitingForAckAfterSendingDisconnectTimeoutInSeconds"

  /**
   * Assumes that Config contains:
   *
   *   OutgoingMessageListenerConfig {
   *     waitingForAckAfterSendingDisconnectTimeoutInSeconds = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): OutgoingMessageListenerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   waitingForAckAfterSendingDisconnectTimeoutInSeconds = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): OutgoingMessageListenerConfig = {
    val waitingForAckAfterSendingDisconnectTimeoutInSeconds = config.getInt(waitingForAckAfterSendingDisconnectTimeoutInSecondsConfigKey)

    new OutgoingMessageListenerConfig(waitingForAckAfterSendingDisconnectTimeoutInSeconds)
  }
}

class OutgoingMessageListenerConfig(
  val waitingForAckAfterSendingDisconnectTimeoutInSeconds: Int
)