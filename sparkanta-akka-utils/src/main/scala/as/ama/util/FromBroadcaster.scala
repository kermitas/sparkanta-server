package as.ama.util

import scala.reflect.ClassTag

class FromBroadcaster[T](val message: T) extends Serializable
