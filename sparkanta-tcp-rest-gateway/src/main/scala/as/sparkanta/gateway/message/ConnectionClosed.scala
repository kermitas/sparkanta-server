package as.sparkanta.gateway.message

import java.net.InetSocketAddress

class ConnectionClosed(
  val throwable:          Option[Throwable],
  val closedByRemoteSide: Boolean,
  val softwareVersion:    Option[Int],
  val remoteAddress:      InetSocketAddress,
  val localAddress:       InetSocketAddress,
  val runtimeId:          Long
) extends Serializable