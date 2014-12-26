package as.sparkanta.device.message.todevice

import as.sparkanta.device.config.pin._

object PinConfiguration {
  lazy final val messageCode: Int = 7
}

class PinConfiguration(
  val d0: DigitalPinConfig,
  val d1: DigitalPinConfig,
  val d2: DigitalPinConfig,
  val d3: DigitalPinConfig,
  val d4: DigitalPinConfig,
  val d5: DigitalPinConfig,
  val d6: DigitalPinConfig,
  val d7: DigitalPinConfig,
  val a0: AnalogPinConfig,
  val a1: AnalogPinConfig,
  val a2: AnalogPinConfig,
  val a3: AnalogPinConfig,
  val a4: AnalogPinConfig,
  val a5: AnalogPinConfig,
  val a6: AnalogPinConfig,
  val a7: AnalogPinConfig

) extends MessageToDevice {

  override def toString = s"${getClass.getSimpleName}"
}