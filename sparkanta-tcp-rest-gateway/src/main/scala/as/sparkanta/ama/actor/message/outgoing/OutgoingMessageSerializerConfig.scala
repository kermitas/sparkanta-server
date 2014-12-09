package as.sparkanta.ama.actor.message.outgoing

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object OutgoingMessageSerializerConfig {

  final val topConfigKey = classOf[OutgoingMessageSerializerConfig].getSimpleName

  final val waitingForAckAfterSendingDisconnectTimeoutInSecondsConfigKey = "waitingForAckAfterSendingDisconnectTimeoutInSeconds"

  /**
   * Assumes that Config contains:
   *
   *   OutgoingMessageSerializerConfig {
   *     waitingForAckAfterSendingDisconnectTimeoutInSeconds = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): OutgoingMessageSerializerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   waitingForAckAfterSendingDisconnectTimeoutInSeconds = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): OutgoingMessageSerializerConfig = {
    val waitingForAckAfterSendingDisconnectTimeoutInSeconds = config.getInt(waitingForAckAfterSendingDisconnectTimeoutInSecondsConfigKey)

    new OutgoingMessageSerializerConfig(waitingForAckAfterSendingDisconnectTimeoutInSeconds)
  }
}

class OutgoingMessageSerializerConfig(
  val waitingForAckAfterSendingDisconnectTimeoutInSeconds: Int
)