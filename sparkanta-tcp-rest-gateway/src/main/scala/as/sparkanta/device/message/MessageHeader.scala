package as.sparkanta.device.message

trait MessageHeader {

  def messageHeaderLength: Int

  def maxMessageLength: Long

  def readMessageLength(messageAsByteArray: Array[Byte]): Int

  def prepareMessageToGo(messageAsByteArray: Array[Byte]): Array[Byte]
}

