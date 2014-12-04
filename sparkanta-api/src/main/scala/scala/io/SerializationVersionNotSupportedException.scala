package scala.io

import java.io.IOException

object SerializationVersionNotSupportedException {
  lazy final val defaultMessage = "Serialization version not supported."
}

/**
 * Thrown during de-externalization when version used during serialization (externalization) is not supported.
 */
class SerializationVersionNotSupportedException(
  val unsupportedSerializationVersion: Byte,
  message:                             String    = SerializationVersionNotSupportedException.defaultMessage,
  cause:                               Throwable = null
) extends IOException(message, cause)