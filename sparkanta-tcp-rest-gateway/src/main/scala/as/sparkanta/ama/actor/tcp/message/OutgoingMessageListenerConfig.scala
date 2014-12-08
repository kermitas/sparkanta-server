/*
package as.sparkanta.ama.actor.tcp.message

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object OutgoingMessageListenerConfig {

  final val topConfigKey = classOf[OutgoingMessageListenerConfig].getSimpleName

  final val maximumNumberOfBufferedMessagesConfigKey = "maximumNumberOfBufferedMessages"
  final val waitingForAckTimeoutInSecondsConfigKey = "waitingForAckTimeoutInSeconds"

  /**
   * Assumes that Config contains:
   *
   *   OutgoingMessageListenerConfig {
   *     maximumNumberOfBufferedMessages = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): OutgoingMessageListenerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   maximumNumberOfBufferedMessages = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): OutgoingMessageListenerConfig = {
    val maximumNumberOfBufferedMessages = config.getInt(maximumNumberOfBufferedMessagesConfigKey)
    val waitingForAckTimeoutInSeconds = config.getInt(waitingForAckTimeoutInSecondsConfigKey)

    new OutgoingMessageListenerConfig(maximumNumberOfBufferedMessages, waitingForAckTimeoutInSeconds)
  }
}

class OutgoingMessageListenerConfig(
  val maximumNumberOfBufferedMessages: Int,
  val waitingForAckTimeoutInSeconds:   Int
)
*/ 