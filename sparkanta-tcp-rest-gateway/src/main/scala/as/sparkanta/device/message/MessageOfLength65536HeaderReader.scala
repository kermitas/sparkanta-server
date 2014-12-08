package as.sparkanta.device.message

import java.io.{ ByteArrayOutputStream, ByteArrayInputStream, DataOutputStream, DataInputStream }

class MessageOfLength65536HeaderReader extends MessageLengthHeaderReader {

  lazy final val messageHeaderLength: Int = 2

  lazy final val maxMessageLength: Long = Math.pow(256, messageHeaderLength).toLong

  override def readMessageLength(messageAsByteArray: Array[Byte]): Int = if (messageAsByteArray.length >= messageHeaderLength) {
    new DataInputStream(new ByteArrayInputStream(messageAsByteArray)).readShort
  } else {
    throw new IllegalArgumentException(s"Passed byte array length should be at least $messageHeaderLength bytes long (currently it is ${messageAsByteArray.length}).")
  }

  override def prepareMessageToGo(messageAsByteArray: Array[Byte]): Array[Byte] = if (messageAsByteArray.length <= maxMessageLength) {
    val baos = new ByteArrayOutputStream
    val dos = new DataOutputStream(baos)
    dos.writeShort(messageAsByteArray.length)
    dos.write(messageAsByteArray)
    dos.flush
    baos.toByteArray
  } else {
    throw new IllegalArgumentException(s"Passed array length can be maximally $maxMessageLength (currently it is ${messageAsByteArray.length}).")
  }
}
