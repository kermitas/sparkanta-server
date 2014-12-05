package scala.io

import java.io.IOException

object SerializationVersionNotSupportedException {
  lazy final val defaultMessagePattern = "Serialization version %s not supported."
  lazy final val defaultMessage = defaultMessagePattern.format("")

  def apply(unsupportedSerializationVersion: Int, cause: Throwable = null): SerializationVersionNotSupportedException =
    new SerializationVersionNotSupportedException(unsupportedSerializationVersion, defaultMessagePattern.format(unsupportedSerializationVersion), cause)
}

/**
 * Thrown during de-externalization when version used during serialization (externalization) is not supported.
 */
class SerializationVersionNotSupportedException(
  val unsupportedSerializationVersion: Int,
  message:                             String    = SerializationVersionNotSupportedException.defaultMessage,
  cause:                               Throwable = null
) extends IOException(message, cause)