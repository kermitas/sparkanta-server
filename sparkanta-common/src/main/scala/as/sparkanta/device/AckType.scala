package as.sparkanta.device

object AckType {
  def apply(ackType: Int) = ackType match {
    case NoAck.ackType       => NoAck
    case ReceivedAck.ackType => ReceivedAck
  }
}

sealed abstract class AckType(val ackType: Int) extends Serializable {

  require(ackType >= 0 && ackType <= 255, s"Ack type ($ackType) can be only between 0 and 255.")

  override def toString = s"${getClass.getSimpleName}(ackType=$ackType)"

}

object NoAck extends AckType(0)
object ReceivedAck extends AckType(1)