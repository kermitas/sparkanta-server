package as.sparkanta.actor.device1.message.serialize

import akka.actor.{ ActorRef, Cancellable, FSM }
import akka.util.{ FSMSuccessOrStop, InternalMessage }
import as.sparkanta.gateway.{ Device, DeviceAck }
import as.sparkanta.device.message.todevice.{ NoAck => DeviceNoAck }
import as.sparkanta.actor.message.serializer.{ Serializer => GeneralSerializer }
import scala.collection.mutable.ListBuffer

object SerializerWorker {
  sealed trait State extends Serializable
  case object WaitingForDataToSend extends State
  case object WaitingForSendDataResult extends State
  case object WaitingForDeviceAck extends State

  sealed trait StateData extends Serializable
  case object WaitingForDataToSendStateData extends StateData
  case class WaitingForSendDataResultStateData(current: Record, timeout: Cancellable, timeoutInMs: Long) extends StateData
  case class WaitingForDeviceAckStateData(current: Record, timeout: Cancellable, timeoutInMs: Long) extends StateData

  object SendDataResultTimeout extends InternalMessage
  object DeviceAckTimeout extends InternalMessage

  class Record(val sendMessage: Device.SendMessage, val sendMessageSender: ActorRef, val serializedMessage: Array[Byte])

  class SerializeWithSendMessage(val sendMessage: Device.SendMessage, val sendMessageSender: ActorRef)
    extends GeneralSerializer.Serialize(sendMessage.messageToDevice, if (sendMessage.ack.isInstanceOf[DeviceAck]) sendMessage.ack.asInstanceOf[DeviceAck].deviceAck else DeviceNoAck)
}

class SerializerWorker(id: Long, broadcaster: ActorRef, var deviceActor: ActorRef, maximumQueuedSendDataMessages: Long)
  extends FSM[SerializerWorker.State, SerializerWorker.StateData] with FSMSuccessOrStop[SerializerWorker.State, SerializerWorker.StateData] {

  import SerializerWorker._

  protected val buffer = new ListBuffer[Record]

}
