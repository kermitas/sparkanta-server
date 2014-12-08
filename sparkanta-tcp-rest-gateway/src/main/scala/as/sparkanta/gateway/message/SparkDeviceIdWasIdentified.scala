package as.sparkanta.gateway.message

import java.net.InetSocketAddress
import akka.actor.ActorRef

class SparkDeviceIdWasIdentified(
  val sparkDeviceId:   String,
  val softwareVersion: Int,
  val remoteAddress:   InetSocketAddress,
  val localAddress:    InetSocketAddress,
  val runtimeId:       Long
) extends Serializable
