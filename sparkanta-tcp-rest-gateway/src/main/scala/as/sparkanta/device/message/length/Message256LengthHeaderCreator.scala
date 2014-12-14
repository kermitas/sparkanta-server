package as.sparkanta.device.message.length

import java.io.{ ByteArrayOutputStream, ByteArrayInputStream }

class Message256LengthHeaderCreator extends MessageLengthHeaderCreator(1) {

  protected override def safeReadMessageLength(bytes: Array[Byte]): Int = new ByteArrayInputStream(bytes).read

  protected override def safePrepareMessageLengthHeader(countOfBytesToSend: Int): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    baos.write(countOfBytesToSend)
    baos.toByteArray
  }
}
