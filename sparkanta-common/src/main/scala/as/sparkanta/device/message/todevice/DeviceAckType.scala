package as.sparkanta.device.message.todevice

object DeviceAckType {
  def apply(ackType: Int) = ackType match {
    case NoAck.ackType       => NoAck
    case ReceivedAck.ackType => ReceivedAck
  }
}

sealed abstract class DeviceAckType(val ackType: Int) extends Serializable {

  require(ackType >= 0 && ackType <= 255, s"Ack type ($ackType) can be only between 0 and 255.")

  override def toString = s"${getClass.getSimpleName}(ackType=$ackType)"

}

object NoAck extends DeviceAckType(0)
object ReceivedAck extends DeviceAckType(1)