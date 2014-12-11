package as.sparkanta.actor.message.incoming

import akka.util.{ ByteString, CompactByteString }
import as.sparkanta.device.message.MessageFormDevice
import as.sparkanta.device.message.length.MessageLengthHeader
import as.sparkanta.device.message.deserialize.Deserializer

class BufferedMessageFromDeviceReader(messageHeader: MessageLengthHeader, deserializer: Deserializer[MessageFormDevice]) {

  protected var buffer: ByteString = CompactByteString.empty
  protected var incomingMessageLength: Option[Int] = None

  def bufferIncomingData(data: ByteString): Unit = buffer = buffer ++ data

  def getMessageFormDevice: Option[MessageFormDevice] = incomingMessageLength match {
    case Some(incomingMessageLength) => if (buffer.size >= incomingMessageLength) {
      this.incomingMessageLength = None

      val aAndB = buffer.splitAt(incomingMessageLength)

      val messageFormDevice = deserializer.deserialize(aAndB._1.toArray)
      buffer = aAndB._2
      Some(messageFormDevice)
    } else {
      None
    }

    case None => if (buffer.size >= messageHeader.messageHeaderLength) {

      val aAndB = buffer.splitAt(messageHeader.messageHeaderLength)

      incomingMessageLength = Some(messageHeader.readMessageLength(aAndB._1.toArray))
      buffer = aAndB._2

      getMessageFormDevice
    } else {
      None
    }
  }
}
