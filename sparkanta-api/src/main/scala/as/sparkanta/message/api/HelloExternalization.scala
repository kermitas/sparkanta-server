package as.sparkanta.message.api

import java.io.{ ObjectInput, ObjectOutput, Externalizable }
import scala.io.SerializationVersionNotSupportedException

trait HelloExternalization { self: Hello =>

  protected lazy final val currentExternalizer = new HelloExternalizationVersion1(self)

  protected lazy final val externalizers = Map[Byte, Externalizable](
    1.toByte -> currentExternalizer
  )

  /**
   * Will serialize using current version.
   */
  override def writeExternal(out: ObjectOutput) = currentExternalizer.writeExternal(out)

  /**
   * Will deserialize using current version and also should support all previous versions.
   */
  override def readExternal(in: ObjectInput) = {

    val version = in.readByte

    externalizers.get(version) match {
      case Some(externalizer) => externalizer.readExternal(in)
      case None               => throw new SerializationVersionNotSupportedException(version)
    }
  }
}

class HelloExternalizationVersion1(hello: Hello) extends Externalizable {

  /**
   * Serialization.
   */
  override def writeExternal(out: ObjectOutput) = ??? // not supported

  /**
   * Deserialization.
   */
  override def readExternal(in: ObjectInput) {
    val deviceIdLength = in.readByte
    val byteArray = new Array[Byte](deviceIdLength)
    in.readFully(byteArray)
    hello.deviceId = new String(byteArray)
  }
}