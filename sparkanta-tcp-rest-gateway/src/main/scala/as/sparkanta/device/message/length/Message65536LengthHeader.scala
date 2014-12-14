package as.sparkanta.device.message.length

import java.io.{ ByteArrayOutputStream, ByteArrayInputStream, DataOutputStream, DataInputStream }

class Message65536LengthHeader extends MessageLengthHeader(2) {

  protected override def safeReadMessageLength(messageAsByteArray: Array[Byte]): Int = new DataInputStream(new ByteArrayInputStream(messageAsByteArray)).readShort

  protected override def safePrepareMessageToGo(messageAsByteArray: Array[Byte]): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    val dos = new DataOutputStream(baos)
    dos.writeShort(messageAsByteArray.length)
    dos.write(messageAsByteArray)
    dos.flush
    baos.toByteArray
  }
}
