package as.sparkanta.ama.actor.restforwarder

import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.gateway.message.ForwardToRestServer
import as.akka.broadcaster.Broadcaster

class RestForwarder(amaConfig: AmaConfig, localIp: String, localPort: Int, restIp: String, restPort: Int) extends Actor with ActorLogging {

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new RestForwarderClassifier(localIp, localPort))
  }

  override def receive = {
    case ftrs: ForwardToRestServer => forwardToRestServer(ftrs)
    case message                   => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def forwardToRestServer(ftrs: ForwardToRestServer): Unit = {
    // TODO: connect, perform REST PUT (with ftrs serialized to JSON), read response, close connection
    // TODO: on any problem throw exception
  }
}
