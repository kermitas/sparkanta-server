package scala.net

import java.net.InetSocketAddress

class IdentifiedInetSocketAddress(val id: Long, val ip: String, val port: Int) extends InetSocketAddress(ip, port) {

  override def toString = s"${getClass.getSimpleName}(id=$id,ip=$ip,port=$port)"

}