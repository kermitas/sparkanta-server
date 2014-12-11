package as.sparkanta.actor.tcp.socket

import akka.util.{ ByteString, CompactByteString }

class BufferedIdentificationStringWithSoftwareVersionReader(identificationString: Array[Byte]) {

  def this(identificationString: String) = this(identificationString.getBytes)

  protected var buffer: ByteString = CompactByteString.empty

  def bufferIncomingData(data: ByteString): Unit = buffer = buffer ++ data

  def getBuffer: ByteString = buffer

  def getSoftwareVersion: Option[Int] = if (buffer.size >= identificationString.length + 1) {
    val is = readIdentificationString
    if (is.corresponds(identificationString) { _ == _ }) {
      Some(readSoftwareVersion)
    } else {
      throw new Exception(s"Received identification string '${new String(is)}' does not match '${new String(identificationString)}'.")
    }
  } else {
    None
  }

  protected def readIdentificationString: Array[Byte] = {
    val identificationStringAndRest = buffer.splitAt(identificationString.length)
    buffer = identificationStringAndRest._2
    identificationStringAndRest._1.toArray
  }

  protected def readSoftwareVersion: Int = {
    val softwareVersionAndRest = buffer.splitAt(1)
    buffer = softwareVersionAndRest._2
    softwareVersionAndRest._1(0)
  }
}
