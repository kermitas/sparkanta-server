package as.sparkanta.gateway.message

import java.net.InetSocketAddress

class DeviceIsDown(
  val runtimeId:        Long,
  val sparkDeviceId:    String,
  val softwareVersion:  Int,
  val remoteAddress:    InetSocketAddress,
  val localAddress:     InetSocketAddress,
  val timeInSystemInMs: Long
) extends ForwardToRestServer