package as.sparkanta.gateway

object HardwareVersion {
  def apply(hardwareVersion: Int) = hardwareVersion match {
    case Virtual.hardwareVersion     => Virtual
    case SparkCore.hardwareVersion   => SparkCore
    case SparkPhoton.hardwareVersion => SparkPhoton
    case unknownHardwareVersion      => throw new IllegalArgumentException(s"Unsupported hardware version $unknownHardwareVersion.")
  }
}

abstract class HardwareVersion(val hardwareVersion: Int) extends Serializable

object Virtual extends Virtual

class Virtual extends HardwareVersion(0) {
  override def toString = this.getClass.getSimpleName
}

object SparkCore extends SparkCore

class SparkCore extends HardwareVersion(1) {
  override def toString = this.getClass.getSimpleName
}

object SparkPhoton extends SparkPhoton

class SparkPhoton extends HardwareVersion(2) {
  override def toString = this.getClass.getSimpleName
}
