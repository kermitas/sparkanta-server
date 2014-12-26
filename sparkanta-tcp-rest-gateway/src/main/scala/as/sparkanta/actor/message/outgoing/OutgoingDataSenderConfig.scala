/*
package as.sparkanta.actor.message.outgoing

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Configuration read from JSON (HOCON) file.
 */
object OutgoingDataSenderConfig {

  final val topConfigKey = classOf[OutgoingDataSenderConfig].getSimpleName

  final val maximumNumberOfBufferedMessagesConfigKey = "maximumNumberOfBufferedMessages"
  final val waitingForAckTimeoutInSecondsConfigKey = "waitingForAckTimeoutInSeconds"

  /**
   * Assumes that Config contains:
   *
   *   OutgoingDataSender {
   *     maximumNumberOfBufferedMessages = ...
   *     ...
   *   }
   */
  def fromTopKey(c: Config = ConfigFactory.load): OutgoingDataSenderConfig = apply(c.getConfig(topConfigKey))

  /**
   * Assumes that Config contains:
   *
   *   maximumNumberOfBufferedMessages = ...
   *   ...
   */
  def apply(config: Config = ConfigFactory.load): OutgoingDataSenderConfig = {
    val maximumNumberOfBufferedMessages = config.getInt(maximumNumberOfBufferedMessagesConfigKey)
    val waitingForAckTimeoutInSeconds = config.getInt(waitingForAckTimeoutInSecondsConfigKey)

    new OutgoingDataSenderConfig(maximumNumberOfBufferedMessages, waitingForAckTimeoutInSeconds)
  }
}

class OutgoingDataSenderConfig(
  val maximumNumberOfBufferedMessages: Int,
  val waitingForAckTimeoutInSeconds:   Int
)
*/ 