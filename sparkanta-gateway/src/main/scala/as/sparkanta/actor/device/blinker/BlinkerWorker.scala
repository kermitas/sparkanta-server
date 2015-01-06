package as.sparkanta.actor.device.blinker

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor._
import as.akka.broadcaster.Broadcaster
import as.sparkanta.gateway.{ TcpAck, Device, DeviceAck, NoAck }
import as.sparkanta.device.config.pin._
import as.sparkanta.device.message.todevice.{ PinConfiguration, SetDigitalPinValue }
import as.sparkanta.device.message.todevice.ReceivedAck

class BlinkerWorker(id: Long, broadcaster: ActorRef) extends Actor with ActorLogging {

  protected val ack = new DeviceAck(500, ReceivedAck)
  //protected val ack = new TcpAck(500)
  protected val high = new Device.SendMessage(id, new SetDigitalPinValue(D7, High), ack)
  protected val low = new Device.SendMessage(id, new SetDigitalPinValue(D7, Low), ack)
  protected var isLow = true
  protected var messagesCount = 0L

  override def preStart(): Unit = {
    broadcaster ! new Broadcaster.Register(self, new BlinkerWorkerClassifier(id))

    context.system.scheduler.schedule(1000 millis, 1000 millis, self, true)(context.dispatcher)

    val pinReadTimeInMs = 250

    val pinConfiguration = new PinConfiguration(
      new DigitalPinConfig(D0, new DigitalInput((pinReadTimeInMs + 0).toChar, EachDigitalProbeValue)),
      new DigitalPinConfig(D1, new DigitalInput((pinReadTimeInMs + 0).toChar, EachDigitalProbeValue)),
      new DigitalPinConfig(D2, new DigitalInput((pinReadTimeInMs + 0).toChar, EachDigitalProbeValue)),
      new DigitalPinConfig(D3, new DigitalInput((pinReadTimeInMs + 0).toChar, EachDigitalProbeValue)),
      new DigitalPinConfig(D4, new DigitalInput((pinReadTimeInMs + 0).toChar, EachDigitalProbeValue)),
      new DigitalPinConfig(D5, new DigitalInput((pinReadTimeInMs + 0).toChar, EachDigitalProbeValue)),
      new DigitalPinConfig(D6, new DigitalInput((pinReadTimeInMs + 0).toChar, EachDigitalProbeValue)),
      new DigitalPinConfig(D7, new DigitalOutput(Low)),
      new AnalogPinConfig(A0, new AnalogInput((pinReadTimeInMs + 0).toChar, EachAnalogProbeValue)),
      new AnalogPinConfig(A1, new AnalogInput((pinReadTimeInMs + 0).toChar, EachAnalogProbeValue)),
      new AnalogPinConfig(A2, new AnalogInput((pinReadTimeInMs + 0).toChar, EachAnalogProbeValue)),
      new AnalogPinConfig(A3, new AnalogInput((pinReadTimeInMs + 0).toChar, EachAnalogProbeValue)),
      new AnalogPinConfig(A4, new AnalogInput((pinReadTimeInMs + 0).toChar, EachAnalogProbeValue)),
      new AnalogPinConfig(A5, new AnalogInput((pinReadTimeInMs + 0).toChar, EachAnalogProbeValue)),
      new AnalogPinConfig(A6, new AnalogInput((pinReadTimeInMs + 0).toChar, EachAnalogProbeValue)),
      new AnalogPinConfig(A7, new AnalogInput((pinReadTimeInMs + 0).toChar, EachAnalogProbeValue))
    )

    broadcaster ! new Device.SendMessage(id, pinConfiguration, NoAck)
    broadcaster ! high
  }

  override def receive = {

    case a: Device.SendMessageSuccessResult => {
      //log.debug(s">>>>>>>>> received send confirmation of ${a.request1.message.messageToDevice.getClass.getSimpleName}")

      messagesCount += 1

      if (isLow)
        broadcaster ! high
      else
        broadcaster ! low

      isLow = !isLow
    }

    case true => {
      log.info(s"**************************** Messages in last second: $messagesCount")
      messagesCount = 0
    }

    case a: Device.SendMessageErrorResult => {
      log.error(a.exception, "Stopping because of sending message problem.")
      broadcaster ! new Device.StopDevice(id, a.exception)
      context.stop(self)
    }

    case a: Device.IdentifiedDeviceDown => {
      log.debug("Stopping because device stopped.")
      context.stop(self)
    }

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }

}