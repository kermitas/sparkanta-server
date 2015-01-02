package as.sparkanta.gateway

import as.sparkanta.device.message.todevice.DeviceAckType

sealed trait AckType extends Serializable {

  override def toString = s"${getClass.getSimpleName}"

}

sealed trait NetworkAck extends AckType

object NoAck extends NetworkAck

class TcpAck(val timeoutInMillis: Long) extends NetworkAck {

  override def toString = s"${getClass.getSimpleName}(timeoutInMillis=$timeoutInMillis)"

}

class DeviceAck(val timeoutInMillis: Long, val deviceAck: DeviceAckType) extends AckType {

  override def toString = s"${getClass.getSimpleName}(deviceAck=$deviceAck,timeoutInMillis=$timeoutInMillis)"

}