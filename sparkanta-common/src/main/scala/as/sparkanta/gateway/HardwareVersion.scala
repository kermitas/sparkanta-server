package as.sparkanta.gateway

object HardwareVersion {
  def apply(hardwareVersion: Int) = hardwareVersion match {
    case Virtual.hardwareVersion      => Virtual
    case SparkCore1.hardwareVersion   => SparkCore1
    case SparkPhoton1.hardwareVersion => SparkPhoton1
    case unknownHardwareVersion       => throw new IllegalArgumentException(s"Unknown hardware version $unknownHardwareVersion.")
  }
}

abstract class HardwareVersion(val hardwareVersion: Int) extends Serializable

object Virtual extends HardwareVersion(0) {
  override def toString = {
    val s = this.getClass.getSimpleName
    s.substring(0, s.length - 1)
  }
}

object SparkCore1 extends HardwareVersion(1) {
  override def toString = {
    val s = this.getClass.getSimpleName
    s.substring(0, s.length - 1)
  }
}

object SparkPhoton1 extends HardwareVersion(2) {
  override def toString = {
    val s = this.getClass.getSimpleName
    s.substring(0, s.length - 1)
  }
}

