package as.sparkanta.ama.actor.tcp.message

import akka.actor.{ ActorLogging, Actor }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import java.net.InetSocketAddress
import as.sparkanta.ama.actor.tcp.connection.TcpConnectionHandler
import akka.io.Tcp
import Tcp._
import akka.util.ByteString

class IncomingMessageListener(amaConfig: AmaConfig, remoteAddress: InetSocketAddress, localAddress: InetSocketAddress) extends Actor with ActorLogging {

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart() {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new IncomingMessageListenerClassifier(context.parent))
  }

  override def receive = {
    case im: TcpConnectionHandler.IncomingMessage => analyzeIncomingMessage(im)
    case message                                  => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def analyzeIncomingMessage(incomingMessage: TcpConnectionHandler.IncomingMessage): Unit = {
    log.info(s"Received ${incomingMessage.messageBody.length} bytes.")
    incomingMessage.tcpActor ! Write(ByteString(incomingMessage.messageBody))
  }
}
