package as.sparkanta.ama.actor.tcp.message

import akka.actor.{ ActorRef, Actor, ActorLogging, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import java.net.InetSocketAddress
import as.sparkanta.internal.message.MessageToDevice
import as.sparkanta.device.message.{ MessageToDevice => MessageToDeviceMarker }

class OutgoingMessageListener(
  amaConfig:     AmaConfig,
  remoteAddress: InetSocketAddress,
  localAddress:  InetSocketAddress,
  tcpActor:      ActorRef,
  runtimeId:     Long
) extends Actor with ActorLogging {

  override val supervisorStrategy = OneForOneStrategy() {
    case t => {
      log.warning(s"Terminating because once of child actors failed ($t).")
      context.stop(self) // TODO: stop FSM actor
      SupervisorStrategy.Escalate
    }
  }

  override def receive = {
    case messageToDevice: MessageToDevice => sendMessageToDevice(messageToDevice.messageToDevice)
    case message                          => log.warning(s"Unhandled $message send by ${sender()}")
  }

  override def preStart(): Unit = {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new OutgoingMessageListenerClassifier(runtimeId))
  }

  protected def sendMessageToDevice(messageToDevice: MessageToDeviceMarker): Unit = {
    // TODO: (change this actor into FSM)
    // TODO: serialize to byte protocol
    // TODO: add to outgoing buffer
    // TODO: if was in stage of waiting for ack then nothing; if was in stage "waiting for something to send" then send this into wire and change state to wait for ack
    // TODO: if was in waiting for ack and ack comes then check if there is something to send, if yes then send and stay in state, if not then back to waiting for something to send
  }
}
