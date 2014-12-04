package akka.util

import java.util.concurrent.atomic.AtomicLong

/**
 * Automatic generator of names like "MyActor-0", "MyActor-1", "MyActor-2" and so on.
 *
 * TODO: this class should be moved to "AkkaUtils" subproject.
 *
 * @param namePattern for example "MyActor-%s"
 */
class ActorNameGenerator(val namePattern: String, number: AtomicLong) {

  def this(namePattern: String, startNumber: Long) = this(namePattern, new AtomicLong(startNumber))

  def this(namePattern: String) = this(namePattern, 0)

  def getNextName: String = namePattern.format(number.getAndIncrement)
}
