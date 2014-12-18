package as.sparkanta.actor.tcp.socket

import akka.util.{ ByteString, CompactByteString }

class BufferedIdentificationStringWithSoftwareAndHardwareVersionReader(identificationString: Array[Byte]) {

  def this(identificationString: String) = this(identificationString.getBytes)

  protected var buffer: ByteString = CompactByteString.empty

  def bufferIncomingData(data: ByteString): Unit = buffer = buffer ++ data

  def getBuffer: ByteString = buffer

  def getSoftwareAndHardwareVersion: Option[(Int, Int)] = if (buffer.size >= identificationString.length + 2) {
    val is = readIdentificationString
    if (is.corresponds(identificationString) { _ == _ }) {
      Some(readSoftwareAndHardwareVersion)
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

  protected def readSoftwareAndHardwareVersion: (Int, Int) = {
    val softwareVersionAndRest = buffer.splitAt(2)
    buffer = softwareVersionAndRest._2
    (softwareVersionAndRest._1(0), softwareVersionAndRest._1(1))
  }
}
