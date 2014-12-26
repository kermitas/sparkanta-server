package com.typesafe.config

/**
 * Useful functions for working with Typesafe Config.
 */
object ConfigUtil {

  def eventuallyReadIntGreaterThanZero(c: Config, configKey: String): Option[Int] =
    (if (c.hasPath(configKey)) Some(c.getInt(configKey)) else None).flatMap(i => if (i > 0) Some(i) else None)

  def eventuallyReadLongGreaterThanZero(c: Config, configKey: String): Option[Long] =
    (if (c.hasPath(configKey)) Some(c.getLong(configKey)) else None).flatMap(l => if (l > 0) Some(l) else None)
}
