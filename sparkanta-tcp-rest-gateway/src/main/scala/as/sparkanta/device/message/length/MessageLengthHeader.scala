package as.sparkanta.device.message.length

/**
 * @param messageHeaderLength in bytes, for example 1 (that means messages of length from 0 to 256)
 */
abstract class MessageLengthHeader( final val messageHeaderLength: Int) {

  final val maxMessageLength: Int = Math.pow(256, messageHeaderLength).toInt

  def readMessageLength(messageAsByteArray: Array[Byte]): Int = if (messageAsByteArray.length >= messageHeaderLength) {
    safeReadMessageLength(messageAsByteArray)
  } else {
    throw new IllegalArgumentException(s"Passed byte array length should be at least $messageHeaderLength bytes long (currently it is ${messageAsByteArray.length}).")
  }

  protected def safeReadMessageLength(messageAsByteArray: Array[Byte]): Int

  def prepareMessageToGo(messageAsByteArray: Array[Byte]): Array[Byte] = if (messageAsByteArray.length <= maxMessageLength) {
    safePrepareMessageToGo(messageAsByteArray: Array[Byte])
  } else {
    throw new IllegalArgumentException(s"Passed array length can be maximally $maxMessageLength bytes long (currently it is ${messageAsByteArray.length}).")
  }

  protected def safePrepareMessageToGo(messageAsByteArray: Array[Byte]): Array[Byte]
}

