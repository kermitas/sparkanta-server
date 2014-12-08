package as.sparkanta.ama.actor.message.outgoing

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object OutgoingDataListenerConfig {

  final val topConfigKey = classOf[OutgoingDataListenerConfig].getSimpleName

  final val maximumNumberOfBufferedMessagesConfigKey = "maximumNumberOfBufferedMessages"
  final val waitingForAckTimeoutInSecondsConfigKey = "waitingForAckTimeoutInSeconds"

  /**
   * Assumes that Config contains:
   *
   *   OutgoingDataListenerConfig {
   *     maximumNumberOfBufferedMessages = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): OutgoingDataListenerConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   maximumNumberOfBufferedMessages = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): OutgoingDataListenerConfig = {
    val maximumNumberOfBufferedMessages = config.getInt(maximumNumberOfBufferedMessagesConfigKey)
    val waitingForAckTimeoutInSeconds = config.getInt(waitingForAckTimeoutInSecondsConfigKey)

    new OutgoingDataListenerConfig(maximumNumberOfBufferedMessages, waitingForAckTimeoutInSeconds)
  }
}

class OutgoingDataListenerConfig(
  val maximumNumberOfBufferedMessages: Int,
  val waitingForAckTimeoutInSeconds:   Int
)