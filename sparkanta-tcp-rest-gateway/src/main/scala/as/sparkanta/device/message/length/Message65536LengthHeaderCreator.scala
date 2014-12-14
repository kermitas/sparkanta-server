package as.sparkanta.device.message.length

import java.io.{ ByteArrayOutputStream, ByteArrayInputStream, DataOutputStream, DataInputStream }

class Message65536LengthHeaderCreator extends MessageLengthHeaderCreator(2) {

  protected override def safeReadMessageLength(bytes: Array[Byte]): Int = new DataInputStream(new ByteArrayInputStream(bytes)).readShort

  protected override def safePrepareMessageLengthHeader(countOfBytesToSend: Int): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    val dos = new DataOutputStream(baos)
    dos.writeShort(countOfBytesToSend)
    dos.flush
    baos.toByteArray
  }
}
