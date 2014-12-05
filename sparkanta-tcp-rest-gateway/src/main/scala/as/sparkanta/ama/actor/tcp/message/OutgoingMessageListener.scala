package as.sparkanta.ama.actor.tcp.message

import akka.actor.{ ActorRef, Actor, ActorLogging, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import java.net.InetSocketAddress

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
      context.stop(self)
      SupervisorStrategy.Escalate
    }
  }

  override def receive = {

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }

  override def preStart(): Unit = {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new OutgoingMessageListenerClassifier(runtimeId))
  }
}
