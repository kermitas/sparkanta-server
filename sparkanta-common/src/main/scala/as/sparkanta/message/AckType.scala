package as.sparkanta.message

import as.sparkanta.device.{ AckType => DeviceAckType }

sealed trait AckType extends Serializable {

  override def toString = s"${getClass.getSimpleName}"

}

object NoAck extends AckType

class TcpAck(val timeoutInMillis: Long) extends AckType {

  override def toString = s"${getClass.getSimpleName}(timeoutInMillis=$timeoutInMillis)"

}

class DeviceAck(val timeoutInMillis: Long, deviceAck: DeviceAckType) extends AckType {

  override def toString = s"${getClass.getSimpleName}(deviceAck=$deviceAck,timeoutInMillis=$timeoutInMillis)"

}