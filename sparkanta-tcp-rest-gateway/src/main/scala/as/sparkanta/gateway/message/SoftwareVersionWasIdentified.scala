package as.sparkanta.gateway.message

import akka.actor.ActorRef

class SoftwareVersionWasIdentified(
  val softwareVersion: Int,
  val remoteIp:        String,
  val remotePort:      Int,
  val localIp:         String,
  val localPort:       Int,
  val runtimeId:       Long
) extends Serializable
