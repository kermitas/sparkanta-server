/*
package as.sparkanta.device.message.length

/**
 * @param messageHeaderLength in bytes, for example 1 (that means messages of length from 0 to 255)
 */
abstract class MessageLengthHeaderCreator( final val messageHeaderLength: Int) {

  final val maxMessageLength: Int = Math.pow(256, messageHeaderLength).toInt - 1

  def readMessageLength(bytes: Array[Byte]): Int = if (bytes.length >= messageHeaderLength) {
    safeReadMessageLength(bytes)
  } else {
    throw new IllegalArgumentException(s"Passed byte array length should be at least $messageHeaderLength bytes long (currently it is ${bytes.length}).")
  }

  protected def safeReadMessageLength(messageAsByteArray: Array[Byte]): Int

  def prepareMessageLengthHeader(countOfBytesToSend: Int): Array[Byte] = if (countOfBytesToSend <= maxMessageLength) {
    safePrepareMessageLengthHeader(countOfBytesToSend)
  } else {
    throw new IllegalArgumentException(s"Passed array length can be maximally $maxMessageLength bytes long (currently it is $countOfBytesToSend).")
  }

  protected def safePrepareMessageLengthHeader(countOfBytesToSend: Int): Array[Byte]
}

*/ 