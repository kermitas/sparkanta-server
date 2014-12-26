package as.sparkanta.device.message.serialize

import java.io.{ OutputStream, DataOutputStream }
import as.sparkanta.device.message.PinConfiguration
import as.sparkanta.device.config.pin._

class PinConfigurationSerializer extends PinConfigurationSerializerVersion1

object PinConfigurationSerializerVersion1 {
  lazy final val serializationVersion = 1
}

class PinConfigurationSerializerVersion1 extends Serializer[PinConfiguration] {

  import ServerHelloSerializerVersion1._

  override def serialize(pinConfiguration: PinConfiguration, os: OutputStream, messageNumber: Int): Unit = {
    writeHeader(os, PinConfiguration.messageCode, serializationVersion, messageNumber, pinConfiguration.ackType)

    val daos = new DataOutputStream(os)

    serializeDigitalPinWorkMode(pinConfiguration.d0.workMode, daos)
    serializeDigitalPinWorkMode(pinConfiguration.d1.workMode, daos)
    serializeDigitalPinWorkMode(pinConfiguration.d2.workMode, daos)
    serializeDigitalPinWorkMode(pinConfiguration.d3.workMode, daos)
    serializeDigitalPinWorkMode(pinConfiguration.d4.workMode, daos)
    serializeDigitalPinWorkMode(pinConfiguration.d5.workMode, daos)
    serializeDigitalPinWorkMode(pinConfiguration.d6.workMode, daos)
    serializeDigitalPinWorkMode(pinConfiguration.d7.workMode, daos)

    serializeAnalogPinWorkMode(pinConfiguration.a0.workMode, daos)
    serializeAnalogPinWorkMode(pinConfiguration.a1.workMode, daos)
    serializeAnalogPinWorkMode(pinConfiguration.a2.workMode, daos)
    serializeAnalogPinWorkMode(pinConfiguration.a3.workMode, daos)
    serializeAnalogPinWorkMode(pinConfiguration.a4.workMode, daos)
    serializeAnalogPinWorkMode(pinConfiguration.a5.workMode, daos)
    serializeAnalogPinWorkMode(pinConfiguration.a6.workMode, daos)
    serializeAnalogPinWorkMode(pinConfiguration.a7.workMode, daos)
  }

  protected def serializeAnalogPinWorkMode(apwm: AnalogPinWorkMode, daos: DataOutputStream): Unit = {
    daos.write(apwm.workMode)

    apwm match {
      case ao: AnalogOutput => daos.write(ao.initialPinValue)

      case ai: AnalogInput => {
        daos.writeChar(ai.probeTimeInMs)
        serializeAnalogInputPinReadNotification(ai.readNotification, daos)
      }

      case apui: AnalogPullUpInput => {
        daos.writeChar(apui.probeTimeInMs)
        serializeAnalogInputPinReadNotification(apui.readNotification, daos)
      }

      case apdi: AnalogPullDownInput => {
        daos.writeChar(apdi.probeTimeInMs)
        serializeAnalogInputPinReadNotification(apdi.readNotification, daos)
      }
    }
  }

  protected def serializeAnalogInputPinReadNotification(aiprn: AnalogInputPinReadNotification, os: OutputStream): Unit = {
    os.write(aiprn.analogInputPinReadNotificationType)

    aiprn match {
      case EachAnalogProbeValue  =>
      case EachAnalogProbeChange =>
    }
  }

  protected def serializeDigitalPinWorkMode(dpwm: DigitalPinWorkMode, daos: DataOutputStream): Unit = {
    daos.write(dpwm.workMode)

    dpwm match {
      case dot: DigitalOutput => daos.write(dot.initialPinValue.pinValue)

      case di: DigitalInput => {
        daos.writeChar(di.probeTimeInMs)
        serializeDigitalInputPinReadNotification(di.readNotification, daos)
      }

      case dpui: DigitalPullUpInput => {
        daos.writeChar(dpui.probeTimeInMs)
        serializeDigitalInputPinReadNotification(dpui.readNotification, daos)
      }

      case dpdi: DigitalPullDownInput => {
        daos.writeChar(dpdi.probeTimeInMs)
        serializeDigitalInputPinReadNotification(dpdi.readNotification, daos)
      }
    }
  }

  protected def serializeDigitalInputPinReadNotification(diprn: DigitalInputPinReadNotification, os: OutputStream): Unit = {
    os.write(diprn.digitalInputPinReadNotificationType)

    diprn match {
      case EachDigitalProbeValue  =>
      case EachDigitalProbeChange =>
    }
  }
}
