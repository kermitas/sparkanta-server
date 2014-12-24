package as.sparkanta.actor.tcp.socket

import akka.util.{ ByteString, CompactByteString }

class BufferedIdentificationStringWithSoftwareAndHardwareVersionReader(identificationString: Array[Byte]) {

  def this(identificationString: String) = this(identificationString.getBytes)

  protected var buffer: ByteString = CompactByteString.empty

  def bufferIncomingData(data: ByteString): Unit = buffer = buffer ++ data

  def getBuffer: ByteString = buffer

  def getSoftwareAndHardwareVersionAndUniqueName: Option[(Int, Int, String)] = if (buffer.size >= identificationString.length + 3) {

    val deviceUniqueNameLength = buffer(identificationString.length + 2)

    if (buffer.size >= identificationString.length + 3 + deviceUniqueNameLength) {
      val is = readIdentificationString
      if (is.corresponds(identificationString) { _ == _ }) {
        Some(readSoftwareAndHardwareVersionAndDeviceUniqueName(deviceUniqueNameLength))
      } else {
        throw new Exception(s"Received identification string '${new String(is)}' does not match '${new String(identificationString)}'.")
      }

    } else {
      None
    }
  } else {
    None
  }

  protected def readIdentificationString: Array[Byte] = {
    val identificationStringAndRest = buffer.splitAt(identificationString.length)
    buffer = identificationStringAndRest._2
    identificationStringAndRest._1.toArray
  }

  protected def readSoftwareAndHardwareVersionAndDeviceUniqueName(deviceUniqueNameLength: Int): (Int, Int, String) = {
    val buffAndRest = buffer.splitAt(2 + 1 + deviceUniqueNameLength)
    buffer = buffAndRest._2

    (buffAndRest._1(0), buffAndRest._1(1), new String(buffAndRest._1.drop(3).toArray))
  }
}
