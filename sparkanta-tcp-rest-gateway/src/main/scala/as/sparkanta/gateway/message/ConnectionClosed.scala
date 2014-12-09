package as.sparkanta.gateway.message

class ConnectionClosed(
  val throwable:          Option[Throwable],
  val closedByRemoteSide: Boolean,
  val softwareVersion:    Option[Int],
  val remoteIp:           String,
  val remotePort:         Int,
  val localIp:            String,
  val localPort:          Int,
  val runtimeId:          Long
) extends Serializable